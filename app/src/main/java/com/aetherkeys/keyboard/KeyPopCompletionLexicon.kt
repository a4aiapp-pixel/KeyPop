package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Small offline, indexed supplement of contemporary and professional English.
 * Completes a prefix without silently rewriting an unfinished word.
 * The full SCOWL dictionary remains the main word source.
 */
object KeyPopCompletionLexicon {
    private val words = (
        "deliverable deliverables deliverability delivered delivering delivery " +
        "implementation implementations implemented implementing " +
        "configuration configurations configurable configuring " +
        "customization customizations customizable customized " +
        "personalization personalized personalize " +
        "optimization optimizations optimized optimizing " +
        "integration integrations integrated integrating " +
        "compatibility compatible accessibility accessible " +
        "functionality functionalities functional functioning " +
        "reliability reliable reliably robustness robust " +
        "predictability predictive predictions prediction predicting " +
        "autocorrection autocorrect completion completions corrections correction " +
        "mistyped mistyping mistype typo typos incorrectly " +
        "intelligence intelligent intelligently intuitive intuitively " +
        "recognition recognizable recognized recognizer " +
        "dictionary dictionaries vocabulary vocabularies lexicon lexicons " +
        "suggestion suggestions suggested suggesting probability probable " +
        "grammar grammatical grammatically punctuation capitalization capitalized " +
        "translation translations translating translator multilingual " +
        "responsive responsiveness adaptability adaptive " +
        "performance performant acceleration accelerated " +
        "development developer developers deployment deployed deployments " +
        "repository repositories workflow workflows compilation compiled " +
        "installation installable installer installed download downloadable downloaded " +
        "synchronization synchronize synchronized reconnect reconnection " +
        "resumption resumable resumed resuming interruption interrupted " +
        "automation automated automatically authentication authenticated " +
        "authorization authorized verification verified encryption encrypted " +
        "credentials credential security secure protection protected privacy " +
        "documents documentation documentary spreadsheet spreadsheets " +
        "presentation presentations formatting formatted consolidated consolidation " +
        "compliance compliant statutory regulatory regulations " +
        "administration administrative correspondence memorandum memoranda " +
        "reconciliation accounting financial expenditure expenditures budgetary " +
        "forecast forecasting transaction transactions transactional " +
        "taxation taxable withholding assessment assessments audit auditor auditors " +
        "evidence investigation investigative settlement settlements litigation " +
        "analysis analytical analyzing statistics statistical application applications " +
        "registration registrations communication communications " +
        "availability unavailable reservation reservations " +
        "professional professionalism recommendation recommendations " +
        "enhancement enhancements enhanced improving improvements " +
        "distinctive distinct distinctiveness differentiation differentiated " +
        "contemporary sophisticated architecture architectural infrastructure " +
        "interface interfaces usability universal universally independent independently " +
        "specification specifications requirements requirement maintainability " +
        "consistency consistent efficiency efficient " +
        "keyboard keyboards keycap keycaps keypress keystroke keystrokes"
    ).split(Regex("\\s+")).filter { it.length in 3..32 && it.all(Char::isLetter) }
        .distinct()

    private val byPrefix:Map<String,List<String>> = words.groupBy { it.take(2) }
    private val validWords:Set<String> = words.toHashSet()

    fun contains(word:String):Boolean =
        word.lowercase(Locale.ROOT) in validWords

    /**
     * Constant-size prefix-bucket lookup: no full vocabulary scan on a keypress.
     * Exact completions precede speculative spelling edits.
     */
    fun candidates(fragment:String,limit:Int=24):List<String> {
        val prefix=fragment.trim().lowercase(Locale.ROOT)
        if(prefix.length !in 3..30 || !prefix.all { it in 'a'..'z' })return emptyList()
        return byPrefix[prefix.take(2)].orEmpty().asSequence()
            .filter { it.startsWith(prefix) && it!=prefix }
            .sortedWith(compareBy<String>{it.length-prefix.length}.thenBy{it})
            .take(limit.coerceIn(1,64)).toList()
    }
}
