package com.aetherkeys.keyboard

/** A pure, conservative URL/email editing guard.
 *
 * This does not modify the editor's text. The host IME must inspect the field
 * variation and the token surrounding the caret BEFORE reading a large context
 * or invoking spelling/grammar, spacing, phrase learning or candidate insertions.
 * Even a Chrome address bar advertised as generic text must be protected once
 * its current token resembles a URL. Ordinary search phrases still get suggestions.
 */
object KeyPopUrlEditingPolicy {
    data class Decision(
        val protected: Boolean,
        val allowSuggestions: Boolean,
        val allowAutomaticCorrection: Boolean,
        val allowAutomaticSpacing: Boolean,
        val allowAutomaticPunctuation: Boolean,
        val allowPersonalLearning: Boolean
    )

    /** Consider only the token touching the caret, never a URL in an earlier
     * sentence. Looking both ways preserves protection while editing mid-URL. */
    fun touchesUrlOrEmail(beforeCursor: String, afterCursor: String = ""): Boolean {
        val before = beforeCursor.takeLast(512).takeLastWhile { !it.isWhitespace() && it != '<' && it != '>' }
        val after = afterCursor.take(512).takeWhile { !it.isWhitespace() && it != '<' && it != '>' }
        val token = (before + after).trim('"', '\'', '(', ')', '[', ']', '{', '}', ',', ';')
        if (token.isBlank()) return false
        if (Regex("(?i)^(?:https?://|ftp://|www\\.|[a-z][a-z0-9+.-]*://)").containsMatchIn(token))
            return true
        if ('@' in token && Regex("^[^@\\s]+@[^@\\s]*$").matches(token)) return true
        // Domain-like tokens and IPv4 addresses, optionally with a path/query.
        // A standalone full stop after a word does not count as a URL.
        val authority = token.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':')
        if (Regex("(?i)^(?:[a-z0-9-]+\\.)+[a-z]{2,63}$").matches(authority)) return true
        if (Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(authority)) return true
        // On long URLs a bounded context can omit the initial scheme/domain.
        // Path/query syntax is safer to treat as URL than to autocorrect.
        if (token.length >= 6 && ("/" in token || "?" in token || "#" in token) &&
            (token.contains("/") || token.contains("=") || token.contains("%")))
            return true
        return false
    }

    fun decide(isUriField: Boolean, isEmailField: Boolean,
               beforeCursor: String, afterCursor: String = ""): Decision {
        val shield = isUriField || isEmailField || touchesUrlOrEmail(beforeCursor, afterCursor)
        return Decision(
            protected = shield,
            allowSuggestions = !shield,
            allowAutomaticCorrection = !shield,
            allowAutomaticSpacing = !shield,
            allowAutomaticPunctuation = !shield,
            allowPersonalLearning = !shield
        )
    }
}
