package com.aetherkeys.keyboard

import android.content.Context
import android.text.InputType
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Optional service-owned bridge. This class does NOT patch existing KeyPop
 * suggestion rendering; the host IME must call predict() and render the result
 * in the EXISTING 3 suggestion chips, with session/selection verification.
 * All disk work stays off the IME's UI thread. */
class KeyPopAdaptiveServiceController(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("keypop", Context.MODE_PRIVATE)
    private val model = KeyPopAdaptiveLanguageModel()
    private val store = KeyPopAdaptiveHistoryStore(appContext)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "keypop-adaptive-io").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)
    @Volatile private var closed = false
    @Volatile var storageError: String? = null
        private set

    init {
        executor.execute {
            runCatching {
                val snap = store.load()
                model.restore(snap.phrases)
                model.restoreWords(snap.words)
            }.onFailure { storageError = "Previously saved personal predictions are unavailable; not overwritten" }
        }
    }

    /** Called every time the editor package or caret changes, so asynchronous
     * predictions from a previous editor can never populate the new field. */
    fun invalidateSession() { generation.incrementAndGet() }

    private fun decision(info: EditorInfo?, incognito: Boolean): KeyPopAdaptiveEditorPolicy.Decision {
        val inputType = info?.inputType ?: InputType.TYPE_NULL
        val type = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val hidden = variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        val app = info?.packageName.orEmpty()
        val excluded = prefs.getStringSet("adaptive_excluded_apps", emptySet()).orEmpty()
            .contains(app)
        val url = variation in setOf(InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val noPersonal = info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        val flagged = KeyPopAdaptiveEditorPolicy.Context(
            sensitive = hidden || type == InputType.TYPE_CLASS_NUMBER ||
                type == InputType.TYPE_CLASS_PHONE || type == InputType.TYPE_NULL,
            incognito = incognito || noPersonal,
            appExcluded = excluded, urlOrEmail = url)
        return KeyPopAdaptiveEditorPolicy.decide(flagged,
            KeyPopAdaptiveEditorPolicy.Consent(
                personalWords = prefs.getBoolean("adaptive_personal_words", false),
                finishedPhrases = prefs.getBoolean("adaptive_finished_phrases", false),
                addresses = prefs.getBoolean("adaptive_addresses", false)))
    }

    fun predict(beforeCursor: String, afterCursor: String, info: EditorInfo?,
                incognito: Boolean, callback: (List<KeyPopAdaptiveLanguageModel.Candidate>) -> Unit) {
        val seq = generation.incrementAndGet()
        // The host must limit what it reads to a bounded non-sensitive snapshot.
        val policy = decision(info, incognito)
        val tokenProtected = KeyPopUrlEditingPolicy.touchesUrlOrEmail(beforeCursor, afterCursor)
        if (!policy.mayPredictGeneric || tokenProtected) {
            callback(emptyList())
            return
        }
        executor.execute {
            val candidates = runCatching {
                model.predict(beforeCursor.takeLast(220), afterCursor.take(48),
                    personalLearningEnabled = policy.maySuggestPersonal,
                    sensitiveEditor = false, allowAddressMemory = policy.mayRememberAddresses,
                    allowPersonalWords = policy.mayRememberWords,
                    allowPersonalPhrases = policy.mayRememberPhrases)
            }.getOrDefault(emptyList())
            main.post {
                if (!closed && generation.get() == seq) callback(candidates)
            }
        }
    }

    /** Invoke only for a confirmed accepted word; never for pasted text. */
    fun learnAcceptedWord(word: String, info: EditorInfo?, incognito: Boolean) {
        val policy = decision(info, incognito)
        if (!policy.mayRememberWords) return
        executor.execute {
            if (model.learnWord(word, true, false)) persist()
        }
    }

    /** Invoke only when the user explicitly commits a finished draft and
     * never for merely displayed, pasted or incoming app message text. */
    fun learnConfirmedPhrase(phrase: String, info: EditorInfo?, incognito: Boolean) {
        val policy = decision(info, incognito)
        if (!policy.mayRememberPhrases) return
        executor.execute {
            if (model.learnPhrase(phrase, true, false,
                    allowAddressMemory = policy.mayRememberAddresses)) persist()
        }
    }

    private fun persist() {
        if (storageError != null) return // never overwrite an undecryptable vault
        runCatching { store.save(model.snapshot(), model.wordSnapshot()) }
            .onFailure { storageError = "Personal prediction memory could not be saved" }
    }

    fun eraseAll(callback: (Boolean) -> Unit) {
        invalidateSession()
        executor.execute {
            val ok = runCatching { model.clear(); store.erase(); storageError = null }.isSuccess
            main.post { if (!closed) callback(ok) }
        }
    }

    override fun close() { closed = true; invalidateSession(); executor.shutdownNow() }
}
