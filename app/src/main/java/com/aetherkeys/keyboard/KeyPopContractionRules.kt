package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Offline, bounded word-level contractions. Distinguishes the first-person
 * pronoun from genuine uppercase acronyms (IM = instant messaging).
 * No network inference, personal history or changes to URLs/passwords here;
 * the editor policy still governs whether edits may be committed.
 */
object KeyPopContractionRules {
    private val confident=mapOf(
        "im" to "I'm",
        "ive" to "I've",
        "youre" to "you're",
        "theyre" to "they're",
        "weve" to "we've",
        "hes" to "he's",
        "shes" to "she's",
        "thats" to "that's",
        "theres" to "there's",
        "whats" to "what's",
        "dont" to "don't",
        "doesnt" to "doesn't",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "cant" to "can't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "wont" to "won't"
    )
    private val pronounContractions=setOf("im","ive")
    fun correction(typed:String):String? {
        if(typed.length !in 2..20 || !typed.all {it.isLetter()})return null
        // Do not rewrite acronyms, technical identifiers or all-caps user text.
        if(typed.length>1 && typed.all(Char::isUpperCase))return null
        val lower=typed.lowercase(Locale.ROOT)
        val fixed=confident[lower]?:return null
        return when {
            lower in pronounContractions -> fixed
            typed.first().isUpperCase() -> fixed.replaceFirstChar(Char::uppercase)
            else -> fixed
        }
    }
    fun firstPerson(candidate:String):String {
        val lower=candidate.lowercase(Locale.ROOT)
        return when {
            lower=="i" -> "I"
            lower.startsWith("i'") || lower.startsWith("i’") ->
                "I"+candidate.drop(1)
            else -> candidate
        }
    }
}
