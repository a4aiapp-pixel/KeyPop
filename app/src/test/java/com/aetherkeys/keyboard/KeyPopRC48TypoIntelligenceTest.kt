package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Real typing examples that must work offline and without telemetry. */
class KeyPopRC48TypoIntelligenceTest {
    @Test fun droppedAndWrongKeysHaveVerifiedCorrections() {
        val examples=mapOf(
            "thmes" to "themes",
            "miatyped" to "mistyped",
            "googke" to "Google",
            "googlr" to "Google",
            "inteligence" to "intelligence",
            "custmization" to "customization",
            "samsng" to "Samsung",
            "microsfot" to "Microsoft")
        for((typed,expected) in examples)
            assertEquals(typed,expected,KeyPopTypingIntelligence.correction(0,typed))
    }
    @Test fun correctWordsAndOtherLanguagesAreNotOverwritten() {
        for(correct in listOf("themes","mistyped","google","intelligence"))
            assertNull(correct,KeyPopTypingIntelligence.correction(0,correct))
        assertNull(KeyPopTypingIntelligence.correction(1,"googke"))
        assertNull(KeyPopTypingIntelligence.correction(2,"thmes"))
    }
    @Test fun generalizedDistanceHandlesDeletionsReplacementsAndTranspositions() {
        assertEquals(1,KeyPopFuzzyRanker.distanceAtMost2("thmes","themes"))
        assertEquals(1,KeyPopFuzzyRanker.distanceAtMost2("miatyped","mistyped"))
        assertEquals(1,KeyPopFuzzyRanker.distanceAtMost2("googke","google"))
        assertEquals(1,KeyPopFuzzyRanker.distanceAtMost2("mistpyed","mistyped"))
        assertEquals(3,KeyPopFuzzyRanker.distanceAtMost2("abcde","unrelated"))
    }
}
