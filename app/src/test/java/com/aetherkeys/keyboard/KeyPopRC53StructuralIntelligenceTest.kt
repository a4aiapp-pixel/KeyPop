package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Generalization checks use contexts distinct from quality benchmark prompts. */
class KeyPopRC53StructuralIntelligenceTest {
    @Test fun pronounContractionsHonorSubjectAgreement() {
        assertEquals("we don't",KeyPopGrammarRules.suggest("we doesnt",0)?.replacement)
        assertEquals("they don't",KeyPopGrammarRules.suggest("they doesnt",0)?.replacement)
        assertEquals("she doesn't",KeyPopGrammarRules.suggest("she dont",0)?.replacement)
        assertEquals("I don't",KeyPopGrammarRules.suggest("i doesnt",0)?.replacement)
        assertNull(KeyPopGrammarRules.suggest("We don't",0))
        assertNull(KeyPopGrammarRules.suggest("She doesn't",0))
    }
    @Test fun multiwordSyntacticFramesGeneralizeToUnseenObjects() {
        val examples=mapOf(
            "The records " to "are",
            "This claim remains " to "open",
            "Please forward " to "the",
            "We shall proceed " to "with",
            "They might " to "be",
            "It is necessary to ensure " to "that",
            "Tell us whether " to "the",
            "The request was " to "submitted",
            "I need to " to "check",
            "We can examine all of " to "the"
        )
        for((sentence,expected) in examples){
            val offers=KeyPopLinguisticContinuations.ranked(0,sentence)
                .map{it.word}
            assertTrue("No $expected for $sentence: $offers",
                expected in offers.take(3))
        }
    }
    @Test fun safetyKeepsSentenceBoundariesAndOtherLanguagesSeparate() {
        assertTrue(KeyPopLinguisticContinuations.ranked(0,"The records. ").isEmpty())
        assertTrue(KeyPopLinguisticContinuations.ranked(1,"We should ").isEmpty())
        assertTrue(KeyPopLinguisticContinuations.ranked(0," ").isEmpty())
    }
    @Test fun morphologicalRecoveryCoversUnseenErrorMechanisms() {
        val words=setOf("necessary","letter","weather","colleague","resource")
        val known={word:String -> word in words}
        assertTrue("necessary" in
            KeyPopMorphologicalRecovery.suggestions("necessry",known))
        assertTrue("letter" in
            KeyPopMorphologicalRecovery.suggestions("lettter",known))
        assertTrue("weather" in
            KeyPopMorphologicalRecovery.suggestions("weathre",known))
        assertTrue(KeyPopMorphologicalRecovery.suggestions("letter",known).isEmpty())
        assertTrue(KeyPopMorphologicalRecovery.suggestions("My-C0de",known).isEmpty())
    }
}
