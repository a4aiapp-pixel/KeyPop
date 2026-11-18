package com.aetherkeys.keyboard

import java.util.Locale
import kotlin.math.min

/**
 * Small, deterministic on-device suggestion model. No network, SDK telemetry,
 * clipboard access or automatic collection of editor text.
 *
 * Call [learnPhrase] only after the user enabled personal phrase learning and
 * the editor has been classified as non-sensitive. Predictions never mutate
 * the editor; the IME's suggestion-tap handler owns replacement/commit logic.
 */
class KeyPopAdaptiveLanguageModel(
    private val timeMillis: () -> Long = System::currentTimeMillis,
    private val maxRecords: Int = 400
) {
    enum class Kind { GRAMMAR, PERSONAL_WORD, PERSONAL_PHRASE, PERSONAL_NEXT_WORD }
    data class Candidate(
        val replacement: String,
        val display: String,
        val kind: Kind,
        val score: Int
    )
    data class Record(val phrase: String, val uses: Int, val lastUsed: Long)
    data class WordRecord(val word: String, val uses: Int, val lastUsed: Long)

    private data class Token(val value: String, val start: Int, val end: Int) {
        val normalized: String get() = value.lowercase(Locale.ROOT)
    }
    private val tokenMatcher = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*")
    private val memories = LinkedHashMap<String, Record>(maxRecords, 0.75f, true)
    private val personalWords = LinkedHashMap<String, WordRecord>(1500, 0.75f, true)
    private val secretWords = Regex("(?i)\\b(password|passcode|otp|one.time.password|pin|cvv|security.code|verification.code|private.key|recovery.phrase)\\b")
    private val linkLike = Regex("(?i)(https?://|www\\.|\\b[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b)")
    private val cardLike = Regex("(?:\\d[ -]?){13,19}")

    @Synchronized
    fun learnPhrase(raw: String, personalLearningEnabled: Boolean, sensitiveEditor: Boolean,
                    allowAddressMemory: Boolean = false): Boolean {
        if (!personalLearningEnabled || sensitiveEditor) return false
        val phrase = raw.trim().replace(Regex("[\\t\\r\\n]+"), " ")
            .replace(Regex(" {2,}"), " ")
        if (phrase.length !in 12..240 || !isSafeToRemember(phrase) ||
            (isAddressLike(phrase) && !allowAddressMemory)) return false
        val tokens = tokenize(phrase)
        if (tokens.size !in 3..40) return false
        val key = phrase.lowercase(Locale.ROOT)
        val old = memories.remove(key)
        memories[key] = Record(phrase, (old?.uses ?: 0).coerceAtMost(9998) + 1, timeMillis())
        trimRecords()
        return true
    }

    /** Address-like phrases are a separately gated personal-memory class. */
    private fun isAddressLike(value: String): Boolean =
        Regex("(?i)\\b(?:house|flat|plot|street|sector|block)\\s*(?:no\\.?|number|[a-z0-9]+)").containsMatchIn(value) ||
            Regex("(?i)\\b(?:address|postal\\s+code|zip\\s+code)\\b").containsMatchIn(value)

    private fun isSafeToRemember(phrase: String): Boolean =
        !linkLike.containsMatchIn(phrase) && !secretWords.containsMatchIn(phrase) &&
            !cardLike.containsMatchIn(phrase) &&
            !phrase.any { it == '\u0000' || (it.isISOControl() && it != '\n') }

    /** Learn only a completed, user-accepted word (never every keystroke).
     * This lets the personal dictionary improve without storing chat messages. */
    @Synchronized
    fun learnWord(raw: String, personalLearningEnabled: Boolean, sensitiveEditor: Boolean): Boolean {
        if (!personalLearningEnabled || sensitiveEditor) return false
        val word = raw.trim()
        if (word.length !in 2..32 || !word.any { it.isLetter() } ||
            tokenize(word).singleOrNull()?.value != word || !isSafeToRemember(word)) return false
        val key = word.lowercase(Locale.ROOT)
        val old = personalWords.remove(key)
        personalWords[key] = WordRecord(word, (old?.uses ?: 0).coerceAtMost(9998) + 1, timeMillis())
        while (personalWords.size > 1500) personalWords.remove(personalWords.keys.first())
        return true
    }

    @Synchronized
    fun clear() {
        memories.clear()
        personalWords.clear()
    }

    @Synchronized
    fun deletePhrase(phrase: String): Boolean = memories.remove(phrase.lowercase(Locale.ROOT)) != null

    @Synchronized
    fun snapshot(): List<Record> = memories.values.toList()

    @Synchronized
    fun wordSnapshot(): List<WordRecord> = personalWords.values.toList()

    @Synchronized
    fun restoreWords(words: List<WordRecord>) {
        personalWords.clear()
        words.asSequence().take(1500).forEach { item ->
            if (item.word.length in 2..32 && item.uses in 1..9999 &&
                item.word.any(Char::isLetter) &&
                tokenize(item.word).singleOrNull()?.value == item.word &&
                isSafeToRemember(item.word)) {
                personalWords[item.word.lowercase(Locale.ROOT)] = item
            }
        }
    }

    @Synchronized
    fun restore(records: List<Record>) {
        memories.clear()
        records.asSequence().take(maxRecords).forEach { r ->
            if (r.uses in 1..9999 && r.phrase.length in 12..240 && isSafeToRemember(r.phrase) &&
                tokenize(r.phrase).size in 3..40) {
                memories[r.phrase.lowercase(Locale.ROOT)] = r
            }
        }
        trimRecords()
    }

    /**
     * [beforeCursor] ends at the caret; [afterCursor] disallows unsafe phrase
     * insertion in the middle of an existing word. [personalLearningEnabled]
     * also controls USING stored phrases, not just recording them.
     */
    @Synchronized
    fun predict(
        beforeCursor: String,
        afterCursor: String = "",
        personalLearningEnabled: Boolean,
        sensitiveEditor: Boolean,
        maxCandidates: Int = 3,
        allowAddressMemory: Boolean = false,
        allowPersonalWords: Boolean = true,
        allowPersonalPhrases: Boolean = true
    ): List<Candidate> {
        if (sensitiveEditor) return emptyList()
        val before = beforeCursor.takeLast(220)
        val words = tokenize(before)
        val lastIsFragment = before.lastOrNull()?.let { it.isLetterOrDigit() || it == '\'' || it == '’' } == true
        val fragment = if (lastIsFragment) words.lastOrNull()?.normalized.orEmpty() else ""
        val previous = if (lastIsFragment) words.dropLast(1).map(Token::normalized)
            else words.map(Token::normalized)
        val afterFirst = afterCursor.firstOrNull()
        val allowsInsertion = afterFirst == null || afterFirst.isWhitespace() ||
            afterFirst in setOf('.', ',', ';', ':', '!', '?', '،', '؛', '؟', '۔')
        val results = LinkedHashMap<String, Candidate>()
        fun offer(text: String, kind: Kind, score: Int) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return
            if (fragment.isNotEmpty() && !trimmed.lowercase(Locale.ROOT).startsWith(fragment)) return
            if (!allowsInsertion && (kind == Kind.PERSONAL_PHRASE || kind == Kind.PERSONAL_NEXT_WORD)) return
            val key = trimmed.lowercase(Locale.ROOT)
            val display = if (trimmed.length > 31) trimmed.take(29).trimEnd() + "…" else trimmed
            val newValue = Candidate(trimmed, display, kind, score)
            if (results[key]?.score?.let { score > it } != false) results[key] = newValue
        }

        grammar(previous, fragment).forEach { (candidate, score) ->
            offer(matchCase(candidate, fragment, before), Kind.GRAMMAR, score)
        }

        if (personalLearningEnabled && allowPersonalWords && fragment.length >= 2) {
            val now = timeMillis()
            for (word in personalWords.values) {
                if (word.word.startsWith(fragment, ignoreCase = true)) {
                    val days = ((now - word.lastUsed).coerceAtLeast(0) / 86_400_000L).toInt()
                    offer(matchCase(word.word, fragment, before), Kind.PERSONAL_WORD,
                        105 + min(word.uses, 25) * 10 + (40 - days.coerceAtMost(40)))
                }
            }
        }

        if (personalLearningEnabled && allowPersonalPhrases && allowsInsertion) {
            val now = timeMillis()
            for (record in memories.values) {
                if (isAddressLike(record.phrase) && !allowAddressMemory) continue
                val recordWords = tokenize(record.phrase)
                for (i in recordWords.indices) {
                    val thisToken = recordWords[i]
                    if (fragment.isNotEmpty() && !thisToken.normalized.startsWith(fragment)) continue
                    val contextDepth = contextMatch(previous, recordWords, i)
                    // A partial current token plus one preceding exact word
                    // is already a meaningful phrase prefix ("house no"). At a
                    // word boundary insist on two context words, except for a
                    // distinctive preceding token.
                    val strongContext = if (fragment.isNotEmpty()) contextDepth >= 1
                        else contextDepth >= 2 ||
                            (contextDepth == 1 && previous.lastOrNull().orEmpty().length >= 7)
                    val uniquePhraseStart = contextDepth == 0 && fragment.length >= 5 &&
                        i == 0 && thisToken.normalized.startsWith(fragment)
                    if (!strongContext && !uniquePhraseStart) continue
                    val ageDays = ((now - record.lastUsed).coerceAtLeast(0L) / 86_400_000L).toInt()
                    val recency = (50 - ageDays.coerceAtMost(50))
                    val usageBonus = min(record.uses, 12) * 9
                    val score = 220 + contextDepth * 90 + recency + usageBonus
                    val single = thisToken.value
                    offer(matchCase(single, fragment, before), Kind.PERSONAL_NEXT_WORD, score)
                    if (i < recordWords.lastIndex) {
                        // Up to eight following tokens, preserving punctuation.
                        val endToken = recordWords[min(i + 8, recordWords.lastIndex)]
                        val phraseTail = record.phrase.substring(thisToken.start, endToken.end)
                        offer(matchCase(phraseTail, fragment, before), Kind.PERSONAL_PHRASE,
                            score + if (contextDepth >= 2) 80 else 8)
                    }
                }
                // At a boundary, the next word is after the matched suffix.
                if (fragment.isEmpty() && previous.isNotEmpty()) {
                    for (i in 1 until recordWords.size) {
                        val depth = contextMatch(previous, recordWords, i)
                        if (depth < 2 && !(depth == 1 && previous.last().length >= 7)) continue
                        val ageDays = ((now - record.lastUsed).coerceAtLeast(0L) / 86_400_000L).toInt()
                        val score = 220 + depth * 90 + (50 - ageDays.coerceAtMost(50)) +
                            min(record.uses, 12) * 9
                        offer(matchCase(recordWords[i].value, fragment, before), Kind.PERSONAL_NEXT_WORD, score)
                        val last = recordWords[min(i + 8, recordWords.lastIndex)]
                        if (last != recordWords[i]) {
                            offer(matchCase(record.phrase.substring(recordWords[i].start, last.end),
                                fragment, before), Kind.PERSONAL_PHRASE, score + 80)
                        }
                    }
                }
            }
        }
        return results.values.sortedWith(compareByDescending<Candidate> { it.score }
            .thenByDescending { it.replacement.length }).take(maxCandidates.coerceIn(1,8))
    }

    private fun contextMatch(previous: List<String>, memorized: List<Token>, nextIndex: Int): Int {
        var depth = 0
        var p = previous.lastIndex
        var m = nextIndex - 1
        while (p >= 0 && m >= 0 && depth < 5 && previous[p] == memorized[m].normalized) {
            depth++; p--; m--
        }
        return depth
    }

    private fun grammar(previous: List<String>, fragment: String): List<Pair<String, Int>> {
        val last = previous.lastOrNull().orEmpty()
        val second = previous.getOrNull(previous.lastIndex - 1).orEmpty()
        val options = linkedMapOf<String, Int>()
        fun add(word: String, score: Int) {
            if (fragment.isEmpty() || word.startsWith(fragment, true)) options[word] = score
        }
        when (last) {
            "you", "we", "they" -> { add("are", 520); add("have", 320); add("will", 280) }
            "i" -> { add("am", 530); add("have", 310); add("will", 290) }
            "he", "she", "it" -> {
                add("is", 530); add("has", 350); add("will", 280)
                add("goes", 540); add("does", 500); add("works", 460)
            }
            "this", "that", "there" -> { add("is", 480); add("was", 250) }
            "are", "am", "is", "was", "were" -> {
                if (second in setOf("you", "we", "they", "i", "he", "she", "it")) {
                    add("allowed", 575)
                    add("going", 350)
                    add("working", 320)
                    add("available", 290)
                }
            }
            "have", "has", "had" -> { add("been", 420); add("finished", 330) }
            "will", "would", "can", "could", "should" -> {
                add("be", 480); add("have", 360); add("allow", 230)
            }
        }
        // These are corrections of incomplete stems rather than next words.
        if (last in setOf("are", "is", "were", "was") && fragment.startsWith("allow")) {
            add("allowed", 650)
            add("allowing", 300)
        }
        return options.entries.map { it.key to it.value }
    }

    private fun matchCase(text: String, fragment: String, before: String): String {
        if (fragment.isNotEmpty() && before.endsWith(fragment, true)) {
            val original = before.takeLast(fragment.length)
            if (original.all { it.isUpperCase() }) return text.uppercase(Locale.ROOT)
            if (original.firstOrNull()?.isUpperCase() == true)
                return text.replaceFirstChar { it.uppercase(Locale.ROOT) }
        }
        return text
    }

    private fun tokenize(s: String): List<Token> = tokenMatcher.findAll(s)
        .map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

    private fun trimRecords() {
        while (memories.size > maxRecords) memories.remove(memories.keys.first())
    }
}
