package com.aetherkeys.keyboard

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Only explicit export/import can move KeyPop's credential/form vault to a
 * different Android app identity. Normal upgrades of stablev2 need no migration.
 * Portable backups are self-contained AES-256-GCM, password-derived with PBKDF2.
 * No plaintext backup is ever written to shared storage or to clipboard.
 */
object KeyPopPortableVaultBackup {
    private val magic = "KPV04401".toByteArray(Charsets.US_ASCII)
    private const val maxSize = 1_500_000
    private const val iterations = 260_000
    private val random = SecureRandom()

    data class Restored(val logins: Int, val profiles: Int)

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        require(password.length in 12..1024) { "Use a backup password of at least 12 characters" }
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
    private fun contents(context: Context): ByteArray {
        val logins = JSONArray()
        for (account in KeyPopLoginVault(context).list()) {
            logins.put(JSONObject().put("id", account.id).put("target", account.target)
                .put("label", account.label).put("username", account.username)
                .put("password", account.password).put("created", account.created)
                .put("appSigningDigest", account.appSigningDigest))
        }
        val forms = JSONArray()
        for (form in KeyPopFormVault(context).list()) {
            forms.put(JSONObject().put("id", form.id).put("label", form.label)
                .put("values", JSONObject(form.values)).put("created", form.created)
                .put("source", form.source))
        }
        return JSONObject().put("format", "KeyPopPortableVault")
            .put("version", 1).put("logins", logins).put("forms", forms)
            .toString().toByteArray(Charsets.UTF_8)
    }

    fun export(context: Context, destination: Uri, password: String): Restored {
        val plaintext = contents(context)
        require(plaintext.size <= maxSize - 64) { "The private vault is too large to export" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        val ciphertext = cipher.doFinal(plaintext)
        val output = ByteBuffer.allocate(magic.size + salt.size + iv.size + ciphertext.size)
            .put(magic).put(salt).put(iv).put(ciphertext).array()
        context.contentResolver.openOutputStream(destination, "w")?.use { it.write(output) }
            ?: error("Cannot write encrypted backup")
        val root = JSONObject(String(plaintext, Charsets.UTF_8))
        return Restored(root.getJSONArray("logins").length(),root.getJSONArray("forms").length())
    }

    /** Validate ENTIRE archive, authentication and object schema before importing. */
    fun restore(context: Context, input: Uri, password: String): Restored {
        val bytes = context.contentResolver.openInputStream(input)?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var read: Int
            while (stream.read(buf).also { read = it } >= 0) {
                if (read == 0) continue
                if (out.size() + read > maxSize) error("Encrypted backup is too large")
                out.write(buf, 0, read)
            }
            out.toByteArray()
        } ?: error("Cannot read backup file")
        require(bytes.size in 70..maxSize) { "Unrecognized private vault archive" }
        require(bytes.copyOfRange(0, 8).contentEquals(magic)) { "Not a KeyPop private vault backup" }
        val salt = bytes.copyOfRange(8, 24)
        val iv = bytes.copyOfRange(24, 36)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        val root = JSONObject(String(cipher.doFinal(bytes.copyOfRange(36, bytes.size)), Charsets.UTF_8))
        require(root.getString("format") == "KeyPopPortableVault" && root.getInt("version") == 1) {
            "Unsupported backup version"
        }
        val logins = root.getJSONArray("logins")
        val forms = root.getJSONArray("forms")
        require(logins.length() <= 1000 && forms.length() <= 40) { "Too many backup records" }
        val validatedLogins = List(logins.length()) { i ->
            val entry = logins.getJSONObject(i)
            val target = entry.getString("target")
            val user = entry.getString("username")
            val secret = entry.getString("password")
            val id = entry.getString("id")
            require(VaultTarget.canonical(target) == target && user.isNotBlank() && user.length <= 300 &&
                secret.isNotBlank() && secret.length <= 4096 && id.length <= 80) { "Invalid saved login" }
            val signer=entry.optString("appSigningDigest")
            require(signer.isEmpty() || Regex("[a-f0-9]{64}(?::[a-f0-9]{64})*").matches(signer)) {
                "Invalid app certificate fingerprint"
            }
            VaultAccount(id, target, entry.optString("label").take(80), user, secret,
                entry.getLong("created"), signer)
        }
        val validatedProfiles = List(forms.length()) { i ->
            val item = forms.getJSONObject(i)
            val valueObj = item.getJSONObject("values")
            val fields = mutableMapOf<String, String>()
            for (k in valueObj.keys()) {
                require(k in KeyPopFormFields.labels) { "Unknown form key" }
                val v = valueObj.getString(k)
                require(v.length <= 300) { "Form value too long" }
                fields[k] = v
            }
            val label = item.getString("label")
            val id = item.getString("id")
            val source = item.optString("source")
            require(id.length <= 80 && label.isNotBlank() && label.length <= 80 &&
                source.length <= 255 && fields.isNotEmpty()) { "Invalid form profile" }
            KeyPopFormProfile(id,label,fields,item.getLong("created"),source)
        }
        // Deduplicate; existing data takes precedence. No delete/overwrite of old records.
        val accountCount = KeyPopLoginVault(context).mergeAll(validatedLogins)
        val formCount = KeyPopFormVault(context).mergeAll(validatedProfiles)
        return Restored(accountCount, formCount)
    }
}
