package com.aetherkeys.keyboard

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeyPopRC44PrivateAutofillInstrumentedTest {
    @Test fun loginsRemainEncryptedAndExistingEntriesAreUpdatedInsteadOfDuplicated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = KeyPopLoginVault(context)
        val id = System.nanoTime().toString()
        val domain = "test${id}.example.com"
        val username = "test-$id@example.com"
        try {
            assertTrue(vault.save(domain, "private test", username, "temporaryA!"))
            assertTrue(vault.save(domain, "private test", username, "temporaryB!"))
            val items = vault.find(domain).filter { it.username == username }
            assertEquals(1, items.size)
            assertEquals("temporaryB!", items.first().password)
            val encrypted = File(context.filesDir, "keypop-logins.enc").readBytes()
            assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("temporaryB!"))
        } finally {
            vault.find(domain).filter { it.username == username }.forEach { vault.delete(it.id) }
        }
    }

    @Test fun consentBasedFormProfilesSurviveVaultReopenAndRemainEncrypted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val a = KeyPopFormVault(context)
        val unique = System.nanoTime().toString()
        val value = "person-$unique@example.com"
        var profileId = ""
        try {
            assertTrue(a.upsert(null, "RC44 test", mapOf("name" to "Test person", "email" to value)))
            val stored = KeyPopFormVault(context).list().first { it.values["email"] == value }
            profileId = stored.id
            assertEquals("Test person", stored.values["name"])
            val bytes = File(context.filesDir, "keypop-form-profiles.enc").readBytes()
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains(value))
            assertFalse(a.upsert(profileId, "No values", emptyMap()))
            assertEquals(value, KeyPopFormVault(context).list().first { it.id == profileId }.values["email"])
        } finally {
            if (profileId.isNotBlank()) a.delete(profileId)
        }
    }

    @Test fun portableBackupRejectsWrongPasswordAndDoesNotDeleteSavedRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = System.nanoTime().toString()
        val domain = "vault${id}.example.com"
        val login = "user-$id@example.com"
        val name = "RC44 backup $id"
        val accountVault = KeyPopLoginVault(context)
        val formVault = KeyPopFormVault(context)
        val backup = File(context.cacheDir, "rc44-vault-$id.kpv")
        var profileId = ""
        try {
            assertTrue(accountVault.save(domain, name, login, "secret-$id"))
            assertTrue(formVault.upsert(null, name, mapOf("email" to login, "name" to name)))
            profileId = formVault.list().first { it.label == name }.id
            val counts = KeyPopPortableVaultBackup.export(context, Uri.fromFile(backup),
                "test-private-backup-password-2026")
            assertTrue(counts.logins >= 1)
            assertTrue(counts.profiles >= 1)
            val raw = backup.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(raw.contains("secret-$id"))
            assertFalse(raw.contains(login))
            assertTrue(runCatching {
                KeyPopPortableVaultBackup.restore(context, Uri.fromFile(backup), "wrong-backup-password")
            }.isFailure)
            assertEquals("secret-$id",accountVault.find(domain).first { it.username == login }.password)
            // Same records already exist: safe import leaves originals unchanged.
            val restored = KeyPopPortableVaultBackup.restore(context, Uri.fromFile(backup),
                "test-private-backup-password-2026")
            assertEquals(0, restored.logins)
            assertEquals(0, restored.profiles)
        } finally {
            accountVault.find(domain).filter { it.username == login }.forEach { accountVault.delete(it.id) }
            if (profileId.isNotEmpty()) formVault.delete(profileId)
            backup.delete()
        }
    }

    @Test fun androidAutofillIsRegisteredWithSystemPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, KeyPopAutofillService::class.java), 0)
        assertEquals("android.permission.BIND_AUTOFILL_SERVICE", info.permission)
        assertFalse(info.exported.not())
    }
}
