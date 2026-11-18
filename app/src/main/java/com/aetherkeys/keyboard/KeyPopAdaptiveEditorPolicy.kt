package com.aetherkeys.keyboard

/** Non-Android decision layer: callers translate EditorInfo into these flags.
 * Secure fields must not be sampled in the first place, not just discarded
 * after a candidate has been generated. */
object KeyPopAdaptiveEditorPolicy {
    data class Context(val sensitive: Boolean = false,
        val incognito: Boolean = false, val appExcluded: Boolean = false,
        val urlOrEmail: Boolean = false, val paymentOrCode: Boolean = false)
    data class Consent(val personalWords: Boolean = false,
        val finishedPhrases: Boolean = false, val addresses: Boolean = false)
    data class Decision(val mayPredictGeneric: Boolean,
        val maySuggestPersonal: Boolean, val mayRememberWords: Boolean,
        val mayRememberPhrases: Boolean, val mayRememberAddresses: Boolean)

    fun decide(ctx: Context, consent: Consent): Decision {
        if (ctx.sensitive || ctx.paymentOrCode) return Decision(false, false, false, false, false)
        if (ctx.urlOrEmail) return Decision(false, false, false, false, false)
        val personal = !(ctx.incognito || ctx.appExcluded)
        return Decision(
            mayPredictGeneric = true,
            maySuggestPersonal = personal && (consent.personalWords || consent.finishedPhrases),
            mayRememberWords = personal && consent.personalWords,
            mayRememberPhrases = personal && consent.finishedPhrases,
            mayRememberAddresses = personal && consent.finishedPhrases && consent.addresses
        )
    }
}
