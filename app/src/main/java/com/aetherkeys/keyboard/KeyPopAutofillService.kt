package com.aetherkeys.keyboard

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews

internal data class KeyPopAutofillFields(
    val user: AutofillId?,
    val pass: AutofillId?,
    val domain: String?,
    val formIds: Map<String, AutofillId>
) {
    val hasLogin get() = user != null && pass != null
}

/** Inspect only Autofill metadata; do not read a view's text during fill. */
private fun discoverFields(structure: AssistStructure): KeyPopAutofillFields {
    var username: AutofillId? = null
    var password: AutofillId? = null
    val formIds = LinkedHashMap<String, AutofillId>()
    val domains = linkedSetOf<String>()
    fun walk(node: AssistStructure.ViewNode) {
        node.webDomain?.let {
            val value = VaultTarget.canonical(it)
            if (value.isNotEmpty()) domains.add(value)
        }
        val hints = node.autofillHints?.toList().orEmpty()
        val hintText = hints.joinToString(" ").lowercase()
        val clue = (node.idEntry.orEmpty() + " " + node.hint.orEmpty()).lowercase()
        val kind = node.inputType and InputType.TYPE_MASK_CLASS
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = (kind == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )) || (kind == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            hintText.contains("password") || clue.contains("password")
        if (node.autofillType == View.AUTOFILL_TYPE_TEXT) {
            if (isPassword) {
                if (password == null) password = node.autofillId
            } else {
                val login = hintText.contains("username") || hintText.contains("email") ||
                    clue.contains("username") || clue.contains("email") ||
                    clue.contains("login") || clue.contains("userid")
                if (login && username == null) username = node.autofillId
                if (kind == InputType.TYPE_CLASS_TEXT || kind == InputType.TYPE_CLASS_PHONE ||
                    kind == InputType.TYPE_NULL) {
                    val field = KeyPopFormFields.identify(hints, clue)
                    val autofillId = node.autofillId
                    if (field != null && field !in formIds && autofillId != null)
                        formIds[field] = autofillId
                }
            }
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i))
    }
    for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
    val domain = when (domains.size) {
        0 -> null
        1 -> domains.first()
        else -> "" // Conflicting web origins: refuse all credential filling.
    }
    return KeyPopAutofillFields(username, password, domain, formIds)
}

/** No value from ViewNode.getText() is ever read or saved. */
private fun latestTextValues(structure: AssistStructure,
                             ids: Map<String, AutofillId>): Map<String, String> {
    if (ids.isEmpty()) return emptyMap()
    val values = LinkedHashMap<String, String>()
    fun walk(node: AssistStructure.ViewNode) {
        val id = node.autofillId
        val value = node.autofillValue
        if (value != null && value.isText) {
            for ((key, requiredId) in ids) {
                if (requiredId == id) {
                    val text = value.textValue?.toString().orEmpty().trim()
                    if (text.length <= 4096 && text.isNotEmpty()) values[key] = text
                }
            }
        }
        for (i in 0 until node.childCount) walk(node.getChildAt(i))
    }
    for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
    return values
}

/**
 * Android OS provides user consent for SaveInfo; KeyPop stores encrypted data
 * only AFTER onSaveRequest. No keystroke/history scraping of passwords or forms.
 */
class KeyPopAutofillService : AutofillService() {
    private val browserPackages = setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev",
        "org.mozilla.firefox", "com.microsoft.emmx", "com.brave.browser",
        "com.sec.android.app.sbrowser")

    private fun targetFor(structure: AssistStructure, fields: KeyPopAutofillFields): String {
        val caller = structure.activityComponent?.packageName.orEmpty()
        if (caller.isBlank() || caller == packageName) return ""
        // A website credential must never be offered to a different domain.
        // A native Android app is addressed by its exact package name.
        return if (caller in browserPackages) fields.domain.orEmpty() else caller
    }

    private fun presentation(label: String): RemoteViews =
        RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }

    override fun onFillRequest(request: FillRequest, signal: CancellationSignal,
                               callback: FillCallback) {
        if (signal.isCanceled) { callback.onSuccess(null); return }
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
                ?: run { callback.onSuccess(null); return }
            val fields = discoverFields(structure)
            val target = targetFor(structure, fields)
            if (target.isEmpty()) { callback.onSuccess(null); return }
            val secure = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
            if (!secure) { callback.onSuccess(null); return }
            val settings = getSharedPreferences("keypop", Context.MODE_PRIVATE)
            val response = FillResponse.Builder()
            var hasResponse = false
            val hasPassword = fields.pass != null
            if (hasPassword) {
                for (login in KeyPopLoginVault(this).find(target).take(10)) {
                    val auth = Intent(this, KeyPopAutofillUnlockActivity::class.java).apply {
                        putExtra("accountId", login.id)
                        putExtra("target", target)
                        fields.user?.let { putExtra("userId", it) }
                        fields.pass?.let { putExtra("passId", it) }
                        data = Uri.parse("keypop://vault/" + Uri.encode(login.id))
                    }
                    val pending = PendingIntent.getActivity(this, login.id.hashCode(), auth,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    val row = presentation("KeyPop · ${login.label.ifBlank { login.username }}")
                    val builder = Dataset.Builder()
                    fields.user?.let { builder.setValue(it, null, row) }
                    fields.pass?.let { builder.setValue(it, null, row) }
                    response.addDataset(builder.setAuthentication(pending.intentSender).build())
                    hasResponse = true
                }
                if (fields.hasLogin && settings.getBoolean("autofillSaveLogins", true)) {
                    response.setSaveInfo(SaveInfo.Builder(
                        SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                        arrayOf(fields.user!!, fields.pass!!)).build())
                    hasResponse = true
                }
            } else if (fields.formIds.isNotEmpty()) {
                for (profile in KeyPopFormVault(this).list().take(15)) {
                    val keys = fields.formIds.keys.filter { !profile.values[it].isNullOrEmpty() }
                    if (keys.isEmpty()) continue
                    val ids = ArrayList(keys.map { fields.formIds.getValue(it) })
                    val auth = Intent(this, KeyPopFormAutofillUnlockActivity::class.java).apply {
                        putExtra("profileId", profile.id)
                        putExtra("target", target)
                        putStringArrayListExtra("fieldKeys", ArrayList(keys))
                        putParcelableArrayListExtra("fieldIds", ids)
                        data = Uri.parse("keypop://form/" + Uri.encode(profile.id))
                    }
                    val pending = PendingIntent.getActivity(this, profile.id.hashCode(), auth,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    val row = presentation("KeyPop form · ${profile.label}")
                    val builder = Dataset.Builder()
                    for (id in ids) builder.setValue(id, null, row)
                    response.addDataset(builder.setAuthentication(pending.intentSender).build())
                    hasResponse = true
                }
                if (fields.formIds.size >= 2 &&
                    settings.getBoolean("autofillSaveForms", true)) {
                    val ids = fields.formIds.values.toTypedArray()
                    val save = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_GENERIC,
                        arrayOf(ids[0], ids[1]))
                    if (ids.size > 2) save.setOptionalIds(ids.drop(2).toTypedArray())
                    response.setSaveInfo(save.build())
                    hasResponse = true
                }
            }
            callback.onSuccess(if (hasResponse) response.build() else null)
        } catch (_: Exception) {
            // Never log user input or display credentials after a malformed autofill request.
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Android only sends this after the person approves the OS Save dialog.
        try {
            val lock = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!lock.isDeviceSecure) { callback.onSuccess(); return }
            val structure = request.fillContexts.lastOrNull()?.structure
                ?: run { callback.onSuccess(); return }
            val fields = discoverFields(structure)
            val target = targetFor(structure, fields)
            if (target.isBlank()) { callback.onSuccess(); return }
            val settings = getSharedPreferences("keypop", Context.MODE_PRIVATE)
            if (fields.hasLogin && settings.getBoolean("autofillSaveLogins", true)) {
                val values = latestTextValues(structure,
                    mapOf("username" to fields.user!!, "password" to fields.pass!!))
                val user = values["username"].orEmpty()
                val pass = values["password"].orEmpty()
                if (user.isNotBlank() && pass.isNotBlank()) {
                    val caller=structure.activityComponent?.packageName.orEmpty()
                    // Native apps must be verifiable by their installed signing certificate.
                    if (caller !in browserPackages &&
                        KeyPopAppIdentity.signingDigest(this,caller).isBlank()) {
                        callback.onFailure("This app's identity cannot be verified")
                        return
                    }
                    check(KeyPopLoginVault(this).save(target, "Saved by Android Autofill", user, pass))
                }
            } else if (fields.pass == null && fields.formIds.size >= 2 &&
                       settings.getBoolean("autofillSaveForms", true)) {
                val values = latestTextValues(structure, fields.formIds)
                if (values.size >= 2) KeyPopFormVault(this).saveApprovedFields(target, values)
            }
            callback.onSuccess()
        } catch (_: Exception) {
            callback.onFailure("KeyPop could not safely save this form")
        }
    }
}

/** Every credential must be unlocked for this exact target and Android session. */
class KeyPopAutofillUnlockActivity : Activity() {
    private val request = 90
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val lock = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!lock.isDeviceSecure) { finish(); return }
        @Suppress("DEPRECATION")
        val prompt = lock.createConfirmDeviceCredentialIntent("KeyPop secure login",
            "Fill login for ${intent.getStringExtra("target").orEmpty().take(80)}")
        if (prompt == null) { finish(); return }
        @Suppress("DEPRECATION")
        startActivityForResult(prompt, request)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == request && resultCode == RESULT_OK) {
            try {
                val account = KeyPopLoginVault(this).get(intent.getStringExtra("accountId").orEmpty())
                val target = intent.getStringExtra("target").orEmpty()
                if (account != null && VaultTarget.matches(account.target, target)) {
                    @Suppress("DEPRECATION")
                    val user = intent.getParcelableExtra<AutofillId>("userId")
                    @Suppress("DEPRECATION")
                    val pass = intent.getParcelableExtra<AutofillId>("passId")
                    val row = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, "KeyPop login")
                    }
                    val builder = Dataset.Builder()
                    user?.let { builder.setValue(it, AutofillValue.forText(account.username), row) }
                    pass?.let { builder.setValue(it, AutofillValue.forText(account.password), row) }
                    if (user != null || pass != null) setResult(RESULT_OK, Intent()
                        .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build()))
                }
            } catch (_: Exception) { /* Never reveal credentials through failure messages. */ }
        }
        finish()
    }
}

/** Protected, user-selected form profile. No sensitive login fields are included. */
class KeyPopFormAutofillUnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val lock = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!lock.isDeviceSecure) { finish(); return }
        @Suppress("DEPRECATION")
        val prompt = lock.createConfirmDeviceCredentialIntent("KeyPop form Autofill",
            "Fill saved form data for ${intent.getStringExtra("target").orEmpty().take(80)}")
        if (prompt == null) { finish(); return }
        @Suppress("DEPRECATION")
        startActivityForResult(prompt, 91)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 91 && resultCode == RESULT_OK) {
            try {
                val profile = KeyPopFormVault(this).list()
                    .firstOrNull { it.id == intent.getStringExtra("profileId") }
                @Suppress("DEPRECATION")
                val ids = intent.getParcelableArrayListExtra<AutofillId>("fieldIds")
                val keys = intent.getStringArrayListExtra("fieldKeys")
                val target = intent.getStringExtra("target").orEmpty()
                if (profile != null && target.isNotBlank() && ids != null && keys != null &&
                    ids.size == keys.size && ids.size in 1..12 && keys.all { it in KeyPopFormFields.labels }) {
                    val row = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, "KeyPop form profile")
                    }
                    val builder = Dataset.Builder()
                    var added = 0
                    for (i in keys.indices) {
                        val value = profile.values[keys[i]] ?: continue
                        if (value.isNotBlank()) {
                            builder.setValue(ids[i], AutofillValue.forText(value), row)
                            added++
                        }
                    }
                    if (added > 0) setResult(RESULT_OK, Intent().putExtra(
                        AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build()))
                }
            } catch (_: Exception) { /* Autofill failure must not disclose private form values. */ }
        }
        finish()
    }
}
