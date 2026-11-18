package com.aetherkeys.keyboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional encrypted local phrase-memory persistence. The IME must call this
 * on a worker executor, never on its input/UI thread. Private typed phrases
 * are never passed to logs, network calls or analytics.
 *
 * Requires app-private storage and the Android Keystore (minSdk >= 23).
 * The signing/package-specific Android Keystore means previews with different
 * package identities cannot share this data. Provide user-initiated backup
 * and restore if migrating across preview packages.
 */
class KeyPopAdaptiveHistoryStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "keypop-personal-language-v1.aes"))
    private val keyAlias = "keypop_adaptive_phrase_memory_v1"
    private val magic = "KPAH0002".toByteArray(Charsets.US_ASCII)
    data class Snapshot(
        val phrases: List<KeyPopAdaptiveLanguageModel.Record>,
        val words: List<KeyPopAdaptiveLanguageModel.WordRecord>
    )

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Return a snapshot. Failure (including key invalidation) is signalled to
     * the caller instead of silently discarding previously learned phrases. */
    fun load(): Snapshot {
        if (!file.baseFile.exists()) return Snapshot(emptyList(), emptyList())
        require(file.baseFile.length() <= 1_048_576L) { "Personal phrase store exceeds size limit" }
        val sealed = file.openRead().use { it.readBytes() }
        require(sealed.size >= magic.size + 12 + 16) { "Truncated personal phrase store" }
        require(sealed.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Unknown personal phrase store version"
        }
        val iv = sealed.copyOfRange(magic.size, magic.size + 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(sealed, magic.size + 12, sealed.size - magic.size - 12)
        return DataInputStream(ByteArrayInputStream(plain)).use { input ->
            require(input.readInt() == 2) { "Unsupported personal language payload" }
            val count = input.readInt()
            require(count in 0..400) { "Invalid personal phrase record count" }
            val phrases = List(count) {
                val phrase = input.readUTF()
                val uses = input.readInt()
                val lastUsed = input.readLong()
                require(phrase.length in 12..240 && uses in 1..9999 && lastUsed >= 0) {
                    "Invalid personal phrase record"
                }
                KeyPopAdaptiveLanguageModel.Record(phrase, uses, lastUsed)
            }
            val wordCount = input.readInt()
            require(wordCount in 0..1500) { "Invalid personal word record count" }
            val words = List(wordCount) {
                val word = input.readUTF()
                val uses = input.readInt()
                val lastUsed = input.readLong()
                require(word.length in 2..32 && uses in 1..9999 && lastUsed >= 0) {
                    "Invalid personal word record"
                }
                KeyPopAdaptiveLanguageModel.WordRecord(word, uses, lastUsed)
            }
            require(input.available() == 0) { "Trailing bytes in personal language store" }
            Snapshot(phrases, words)
        }
    }

    /** Persist the bounded model snapshot atomically with authenticated AES-GCM. */
    fun save(
        records: List<KeyPopAdaptiveLanguageModel.Record>,
        words: List<KeyPopAdaptiveLanguageModel.WordRecord>
    ) {
        require(records.size <= 400) { "Too many personal phrase records" }
        require(words.size <= 1500) { "Too many personal word records" }
        val plain = ByteArrayOutputStream().also { target ->
            DataOutputStream(target).use { output ->
                output.writeInt(2)
                output.writeInt(records.size)
                for (record in records) {
                    require(record.phrase.length in 12..240 && record.uses in 1..9999 &&
                        record.lastUsed >= 0) { "Invalid personal phrase record" }
                    output.writeUTF(record.phrase)
                    output.writeInt(record.uses)
                    output.writeLong(record.lastUsed)
                }
                output.writeInt(words.size)
                for (word in words) {
                    require(word.word.length in 2..32 && word.uses in 1..9999 &&
                        word.lastUsed >= 0) { "Invalid personal word record" }
                    output.writeUTF(word.word)
                    output.writeInt(word.uses)
                    output.writeLong(word.lastUsed)
                }
            }
        }.toByteArray()
        require(plain.size <= 950_000) { "Personal phrase store too large" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain)
        val result = magic + cipher.iv + sealed
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(result)
            file.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) file.failWrite(stream)
            throw e
        }
    }

    fun erase() {
        file.delete()
    }
}
