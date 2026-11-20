package com.aetherkeys.keyboard

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

/** RC52 checks generalized spelling, first-person grammar and editor privacy. */
class KeyPopRC52ContractionAndFuzzyTest {
    @Test fun imCorrectionsAreImmediatelyAvailableWithCorrectCapitalization() {
        assertEquals("I'm",KeyPopContractionRules.correction("im"))
        assertEquals("I'm",KeyPopContractionRules.correction("Im"))
        assertEquals("I've",KeyPopContractionRules.correction("ive"))
        assertEquals("I'm",KeyPopPredictionContext.wordCase("I'm","im","Hello im"))
        assertEquals("I'm",KeyPopPredictionContext.wordCase("I'm","im","im"))
        assertEquals("I'm",KeyPopGrammarRules.suggest("im",0)?.replacement)
        assertEquals("I'm",KeyPopGrammarRules.suggest("Im",0)?.replacement)
        assertEquals("I'm",KeyPopWritingTools.rewrite("im ready","Proofread",0).substring(0,3))
    }

    @Test fun properAcronymsAndSecureContextsStayProtected() {
        assertNull(KeyPopContractionRules.correction("IM"))
        assertNull(KeyPopContractionRules.correction("IVE"))
        assertNull(KeyPopGrammarRules.suggest("IM",0))
        assertNull(KeyPopContractionRules.correction("im2"))
        assertNull(KeyPopContractionRules.correction("im@example.com"))
        for(type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_NUMBER)) {
            assertFalse("Protected field $type",
                KeyPopTypingRules.autocorrectEnabled(true,type))
        }
    }

    @Test fun otherCommonUnpunctuatedContractionsUseWordLevelPolicy() {
        val cases=mapOf("dont" to "don't","cant" to "can't",
            "youre" to "you're","ive" to "I've","weve" to "we've",
            "didnt" to "didn't","thats" to "that's","shouldnt" to "shouldn't")
        for((typed,expected) in cases)
            assertEquals(typed,expected,KeyPopContractionRules.correction(typed))
        for(valid in listOf("don't","I","I'm","we're","phone"))
            assertNull(KeyPopContractionRules.correction(valid))
    }

    @Test fun generalizedCandidateRankingIncludesThreeLetterSwapsAndUnknownTypos() {
        val context=KeyPopPredictionContext.parse("I read tge","tge")
        val candidates=KeyPopFuzzyRanker.rank("tge",
            sequenceOf("the" to 10,"tie" to 20,"ago" to 20),context,8)
        assertTrue(candidates.any{it.word=="the" && it.distance==1})
        val long=KeyPopFuzzyRanker.rank("googkle",
            sequenceOf("google" to 10,"goggles" to 20),
            KeyPopPredictionContext.parse("find googkle","googkle"),8)
        assertEquals("google",long.first().word)
        assertEquals("google",KeyPopFuzzyRanker.automatic(listOf(long.first())))
    }

    @Test fun confidenceRejectsInconclusiveOrValidWordSubstitutions() {
        val closeTie=listOf(
            KeyPopFuzzyRanker.Match("samra",1,327,10),
            KeyPopFuzzyRanker.Match("same",1,325,10))
        assertNull(KeyPopFuzzyRanker.automatic(closeTie,aggressive=false))
        assertNull(KeyPopFuzzyRanker.automatic(
            listOf(KeyPopFuzzyRanker.Match("samra",1,345,35)),aggressive=true))
        assertNull(KeyPopFuzzyRanker.automatic(emptyList()))
    }
}
