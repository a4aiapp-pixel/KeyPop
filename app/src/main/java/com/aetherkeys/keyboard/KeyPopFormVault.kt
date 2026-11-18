package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only these explicit, non-secret form fields can ever enter the form vault. */
object KeyPopFormFields {
    val labels = linkedMapOf(
        "name" to "Full name",
        "givenName" to "First name",
        "familyName" to "Last name",
        "email" to "Email address",
        "phone" to "Phone number",
        "organization" to "Organization",
        "streetAddress" to "Street address",
        "addressLine2" to "Apartment / unit",
        "city" to "City",
        "region" to "Province / state",
        "postalCode" to "Postal code",
        "country" to "Country"
    )
    /** Android autofill hints are preferred over ambiguous website-provided IDs. */
    fun identify(hints: Collection<String>, clue: String): String? {
        val hintsLower = hints.map { it.lowercase().replace("_", "").replace("-", "") }
        val hintMapping = mapOf(
            "name" to "name", "personname" to "name", "fullname" to "name",
            "givenname" to "givenName", "firstname" to "givenName", "familyname" to "familyName",
            "lastname" to "familyName", "emailaddress" to "email", "email" to "email",
            "phonenumber" to "phone", "phone" to "phone", "tel" to "phone",
            "postaladdress" to "streetAddress", "streetaddress" to "streetAddress",
            "addressline1" to "streetAddress", "addressline2" to "addressLine2",
            "addresslevel2" to "city", "city" to "city", "addresslocality" to "city",
            "addresslevel1" to "region", "region" to "region", "state" to "region",
            "postalcode" to "postalCode", "zipcode" to "postalCode", "zip" to "postalCode",
            "country" to "country", "countryname" to "country",
            "organization" to "organization", "company" to "organization"
        )
        for (hint in hintsLower) hintMapping[hint]?.let { return it }
        if (hintsLower.isNotEmpty()) return null // Unknown hints should not be guessed.
        val c = clue.lowercase().replace(Regex("[^a-z0-9]+"), "")
        if (c.contains("password") || c.contains("card") || c.contains("security") ||
            c.contains("pin") || c.contains("otp") || c.contains("secret") ||
            c.contains("token") || c.contains("cvv") || c.contains("verification")) return null
        return when {
            c in setOf("name", "fullname", "yourname", "contactname") -> "name"
            c.contains("firstname") || c.contains("givenname") -> "givenName"
            c.contains("lastname") || c.contains("familyname") || c.contains("surname") -> "familyName"
            c.contains("email") -> "email"
            c.contains("phone") || c.contains("telephone") || c in setOf("tel", "mobile") -> "phone"
            c.contains("organization") || c.contains("companyname") -> "organization"
            c.contains("addressline2") || c.contains("apartment") || c.contains("suite") -> "addressLine2"
            c in setOf("address", "street", "streetaddress", "addressline1") -> "streetAddress"
            c.contains("city") || c.contains("town") -> "city"
            c.contains("province") || c in setOf("state", "region") -> "region"
            c.contains("postal") || c.contains("zipcode") -> "postalCode"
            c.contains("country") -> "country"
            else -> null
        }
    }
    fun sanitize(values: Map<String, String>): Map<String, String> = values
        .filterKeys { it in labels.keys }
        .mapValues { (_, value) -> value.trim().take(300) }
        .filterValues { it.isNotEmpty() }
}

data class KeyPopFormProfile(
    val id: String,
    val label: String,
    val values: Map<String, String>,
    val created: Long,
    val source: String = ""
)

/** AES-256-GCM with a separate non-exportable Android Keystore key. */
class KeyPopFormVault(private val context: Context) {
    private val file = File(context.filesDir, "keypop-form-profiles.enc")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("KeyPopFormProfilesAES", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("KeyPopFormProfilesAES",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(data)
    }
    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size >= 29 && data.size <= 256_000) { "Invalid encrypted form vault" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size))
    }
    @Synchronized fun list(): List<KeyPopFormProfile> {
        if (!file.exists()) return emptyList()
        val all = JSONArray(String(decrypt(file.readBytes()), Charsets.UTF_8))
        require(all.length() <= 40) { "Too many form entries" }
        return List(all.length()) { i ->
            val item = all.getJSONObject(i)
            val vals = item.getJSONObject("values")
            val mapped = LinkedHashMap<String, String>()
            for (key in vals.keys()) {
                require(key in KeyPopFormFields.labels) { "Unknown form field" }
                mapped[key] = vals.getString(key).take(300)
            }
            KeyPopFormProfile(item.getString("id"), item.getString("label"),
                mapped, item.getLong("created"), item.optString("source"))
        }.sortedByDescending { it.created }
    }
    @Synchronized fun replaceAll(profiles: List<KeyPopFormProfile>) {
        require(profiles.size <= 40) { "Maximum 40 form profiles" }
        val arr = JSONArray()
        for (p in profiles) {
            require(p.id.length <= 80 && p.label.length <= 80 && p.source.length <= 255)
            val safe = KeyPopFormFields.sanitize(p.values)
            require(safe.isNotEmpty()) { "Empty form profile" }
            arr.put(JSONObject().put("id", p.id).put("label", p.label)
                .put("created", p.created).put("source", p.source)
                .put("values", JSONObject(safe)))
        }
        val tmp = File(context.filesDir, "keypop-form-profiles.tmp")
        tmp.outputStream().use { it.write(encrypt(arr.toString().toByteArray(Charsets.UTF_8))) }
        if (!tmp.renameTo(file)) { tmp.delete(); error("Could not save form profiles") }
    }
    @Synchronized fun upsert(id: String?, label: String, values: Map<String, String>, source: String = ""): Boolean {
        val safe = KeyPopFormFields.sanitize(values)
        if (safe.isEmpty() || label.isBlank()) return false
        val items = list().toMutableList()
        val existing = items.indexOfFirst { it.id == id && id != null }
        val entry = KeyPopFormProfile(id ?: UUID.randomUUID().toString(), label.trim().take(80),
            safe, System.currentTimeMillis(), source.take(255))
        if (existing >= 0) items[existing] = entry else items.add(entry)
        replaceAll(items)
        return true
    }
    /** A SaveInfo operation follows an explicit Android system confirmation. */
    @Synchronized fun saveApprovedFields(source: String, values: Map<String, String>): Boolean {
        val safe = KeyPopFormFields.sanitize(values)
        if (source.isBlank() || safe.size < 2) return false
        val current = list().toMutableList()
        if (current.any { it.source == source && it.values == safe }) return true
        val i = current.indexOfFirst { it.source == source && it.source.isNotEmpty() }
        val name = "Saved from ${source.take(55)}"
        val record = KeyPopFormProfile(if (i >= 0) current[i].id else UUID.randomUUID().toString(),
            name, safe, System.currentTimeMillis(), source.take(255))
        if (i >= 0) current[i] = record else current.add(record)
        replaceAll(current)
        return true
    }
    @Synchronized fun delete(id: String) { replaceAll(list().filterNot { it.id == id }) }
    @Synchronized fun mergeAll(imported: List<KeyPopFormProfile>): Int {
        val all = list().toMutableList()
        var added = 0
        for (item in imported) {
            if (all.any { it.id == item.id || it.values == item.values }) continue
            all.add(item)
            added++
        }
        replaceAll(all)
        return added
    }
}

/** Explicit, user-editable non-password form profiles. */
class KeyPopFormProfilesActivity : Activity() {
    private val vault by lazy { KeyPopFormVault(this) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) {
            Toast.makeText(this, "Set a device screen lock before saving private profiles", Toast.LENGTH_LONG).show()
            finish(); return
        }
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("KeyPop form profiles", "Unlock private form data")
        if (intent == null) { finish(); return }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 150)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 150 && resultCode == RESULT_OK) showProfiles()
        else finish()
    }
    private fun showProfiles() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        content.addView(TextView(this).apply { text = "Saved form data"; textSize = 23f })
        content.addView(TextView(this).apply {
            text = "Store names, email addresses, phone numbers and addresses for Android Autofill. Passwords are saved separately in the Login Vault. No background typing capture."
        })
        content.addView(Button(this).apply { text = "＋ Add a form profile"; setOnClickListener { edit(null) } })
        try {
            val profiles = vault.list()
            if (profiles.isEmpty()) content.addView(TextView(this).apply { text = "No saved form profiles yet." })
            for (profile in profiles) {
                content.addView(TextView(this).apply {
                    text = "${profile.label}\n${profile.values.keys.mapNotNull { KeyPopFormFields.labels[it] }.joinToString(" · ")}"
                    textSize = 15f
                })
                content.addView(Button(this).apply {
                    text = "Edit ${profile.label}"
                    setOnClickListener { edit(profile) }
                })
                content.addView(Button(this).apply {
                    text = "Delete ${profile.label}"
                    setOnClickListener {
                        AlertDialog.Builder(this@KeyPopFormProfilesActivity).setTitle("Delete form profile?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                runCatching { vault.delete(profile.id) }
                                    .onSuccess { showProfiles() }
                                    .onFailure { notifyError() }
                            }.show()
                    }
                })
            }
        } catch (_: Exception) {
            content.addView(TextView(this).apply { text = "Encrypted form records cannot be opened. Existing data was not modified." })
        }
    }
    private fun notifyError() {
        Toast.makeText(this, "Could not save the encrypted form profile", Toast.LENGTH_LONG).show()
    }
    private fun edit(existing: KeyPopFormProfile?) {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val label = EditText(this).apply {
            hint = "Profile label (Home / Work)"; setSingleLine(true)
            setText(existing?.label ?: "Personal")
        }
        column.addView(label)
        val editors = LinkedHashMap<String, EditText>()
        for ((key, name) in KeyPopFormFields.labels) {
            val edit = EditText(this).apply {
                hint = name; setSingleLine(true)
                if (key == "email") inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                if (key == "phone") inputType = InputType.TYPE_CLASS_PHONE
                setText(existing?.values?.get(key).orEmpty())
            }
            editors[key] = edit
            column.addView(edit)
        }
        val scroll = ScrollView(this).apply { addView(column) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "Add form profile" else "Edit form profile")
            .setView(scroll).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val vals = editors.mapValues { it.value.text.toString() }
                val ok = runCatching { vault.upsert(existing?.id, label.text.toString(), vals) }.getOrDefault(false)
                if (ok) showProfiles() else notifyError()
            }.show()
    }
}
