package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/**
 * User-reported acceptance: next-word help must infer grammatical
 * prepositions from many contexts, not solely a memorized exact sentence.
 */
class KeyPopRC52LinguisticContinuationTest {
    @Test fun keepMePostedRanksAllThreeNaturalPrepositions() {
        val suggestions=KeyPopUnifiedPrediction.rank(0,"","keep me posted ",
            emptyList(),emptyList(),limit=3)
        assertEquals("on",suggestions.first())
        assertEquals(setOf("on","about","regarding"),suggestions.toSet())
    }

    @Test fun syntaxGeneralizesAcrossRecipientsAndVerbVariants() {
        for(phrase in listOf("Please keep me posted ",
            "keep us posted ","keep the team posted ",
            "keep our board posted ","keep Alex posted ",
            "I will keep you posted ")) {
            val words=KeyPopLinguisticContinuations.ranked(0,phrase).map{it.word}
            assertEquals(phrase,"on",words.first())
            assertTrue(phrase,"regarding" in words)
            assertTrue(phrase,"about" in words)
        }
        val update=KeyPopLinguisticContinuations.ranked(0,"Please update me ")
            .map{it.word}
        assertTrue(update.containsAll(listOf("on","about","regarding")))
        val informed=KeyPopLinguisticContinuations.ranked(0,"Keep me informed ")
            .map{it.word}
        assertTrue(informed.contains("about"))
    }

    @Test fun partialWordProvidesCorrectCompletion() {
        val phrase=KeyPopUnifiedPrediction.rank(0,"reg","keep me posted reg",
            emptyList(),emptyList(),limit=3)
        assertEquals("regarding",phrase.first())
        val preposition=KeyPopUnifiedPrediction.rank(0,"o","keep us posted o",
            emptyList(),emptyList(),limit=3)
        assertEquals("on",preposition.first())
    }

    @Test fun personalPreferenceRanksAmongValidNextWords() {
        val options=KeyPopUnifiedPrediction.rank(0,"","keep me posted ",
            listOf("regarding"),emptyList(),limit=3)
        assertEquals("regarding",options.first())
        assertTrue(options.contains("on"))
        assertTrue(options.contains("about"))
    }

    @Test fun doNotContinueOldSentenceOrWrongLanguage() {
        assertTrue(KeyPopLinguisticContinuations.ranked(0,
            "keep me posted. ").isEmpty())
        assertTrue(KeyPopLinguisticContinuations.ranked(0,
            "Please share a report ").isEmpty())
        assertTrue(KeyPopLinguisticContinuations.ranked(1,
            "keep me posted ").isEmpty())
    }

    @Test fun grammaticalFramesExtendBeyondPostedPhrase() {
        val forward=KeyPopLinguisticContinuations.ranked(0,
            "We are looking forward to ").map{it.word}
        assertEquals("hearing",forward.first())
        assertTrue("working" in forward)
        val thanks=KeyPopLinguisticContinuations.ranked(0,
            "Thank you for ").map{it.word}
        assertEquals("your",thanks.first())
        val agreement=KeyPopLinguisticContinuations.ranked(0,
            "They ").map{it.word}
        assertEquals("are",agreement.first())
    }
}
