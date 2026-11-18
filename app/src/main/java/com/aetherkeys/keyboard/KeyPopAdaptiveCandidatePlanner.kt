package com.aetherkeys.keyboard

import java.util.Locale

/** Pure, immutable suggestion merging and editor replacement planning.
 * The IME must verify the same editor session and selection snapshot before
 * executing any plan through InputConnection on its main/UI thread. */
object KeyPopAdaptiveCandidatePlanner {
    data class EditorPlan(val replaceBeforeChars: Int, val insert: String,
                          val expectedPrefix: String, val expectedSuffix: String)
    data class RankedCandidate(val replacement: String, val display: String,
                               val score: Int, val source: String)

    private val lastToken = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*$")

    fun makePlan(before: String, after: String, candidate: String): EditorPlan? {
        // Never replace URL/email fragments or auto-complete inside web tokens.
        // Chrome can expose its omnibox as generic text rather than TYPE_TEXT_VARIATION_URI.
        if (KeyPopUrlEditingPolicy.touchesUrlOrEmail(before, after)) return null
        val value = candidate.trim()
        if (value.isBlank() || value.length > 240 || '\n' in value || '\r' in value) return null
        val fragment = lastToken.find(before)?.value.orEmpty()
        if (fragment.isNotEmpty() && !value.startsWith(fragment, ignoreCase = true)) return null
        // Word-completion must never split a token or silently clobber a selection.
        if (after.firstOrNull()?.let { it.isLetterOrDigit() || it == '\'' || it == '’' } == true)
            return null
        if (value == fragment) return null
        return EditorPlan(fragment.length, value, before.takeLast(60), after.take(60))
    }

    fun combine(adaptive: List<KeyPopAdaptiveLanguageModel.Candidate>,
                existing: List<RankedCandidate>, maxItems: Int = 3): List<RankedCandidate> {
        val grouped = linkedMapOf<String, RankedCandidate>()
        for (entry in existing + adaptive.map {
            RankedCandidate(it.replacement, it.display, it.score, "adaptive:${it.kind.name}")
        }) {
            if (entry.replacement.isBlank() || entry.replacement.length > 240) continue
            val key = entry.replacement.trim().lowercase(Locale.ROOT)
            if (grouped[key] == null || entry.score > grouped.getValue(key).score)
                grouped[key] = entry
        }
        return grouped.values.sortedWith(compareByDescending<RankedCandidate> { it.score }
            .thenBy { it.replacement.length }).take(maxItems.coerceIn(1, 8))
    }
}
