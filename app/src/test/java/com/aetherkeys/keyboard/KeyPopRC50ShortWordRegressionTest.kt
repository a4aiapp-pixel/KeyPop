package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Reproductions of short deletions that never reached the RC49 correction path. */
class KeyPopRC50ShortWordRegressionTest {
    @Test fun exampleTeIsRecognizedAndCanBeCorrectedInProse() {
        assertEquals("the",KeyPopShortWordRecovery.suggestions("te","see te").first())
        assertEquals("the",KeyPopShortWordRecovery.safeAutomatic("te","see te"))
        assertEquals("the",KeyPopShortWordRecovery.safeAutomatic("Te","See Te"))
        assertEquals("the",KeyPopShortWordRecovery.suggestions("te","please review te").first())
    }
    @Test fun shortEditCandidatesAreGeneratedWithoutWhitelistingEachMisspelling() {
        val cases=mapOf("th" to "the","yu" to "you","an" to "and",
            "wht" to "what","doea" to "does","shuld" to "should",
            "tha" to "that","te" to "the")
        for((typed,wanted) in cases) {
            assertTrue("$typed should suggest $wanted",
                wanted in KeyPopShortWordRecovery.suggestions(typed,"please review $typed",8))
        }
    }
    @Test fun doNotSilentlyChangeAmbiguousShortOrCorrectWords() {
        assertNull(KeyPopShortWordRecovery.safeAutomatic("te","te"))
        assertNull(KeyPopShortWordRecovery.safeAutomatic("te","abc te"))
        for(correct in listOf("we","he","and","to","does","the"))
            assertNull("$correct is valid",KeyPopShortWordRecovery.safeAutomatic(
                correct,"please check $correct"))
        assertNull(KeyPopShortWordRecovery.safeAutomatic("go9d","please review go9d"))
        assertTrue(KeyPopShortWordRecovery.suggestions("3G","3G").isEmpty())
        assertTrue(KeyPopShortWordRecovery.suggestions("AB-02","AB-02").isEmpty())
    }
    @Test fun twoLetterCandidatesReachUnifiedSuggestionRanker() {
        val candidates=KeyPopUnifiedPrediction.rank(0,"te","see te",
            emptyList(),KeyPopShortWordRecovery.suggestions("te","see te",8),
            limit=3)
        assertTrue("Missing 'the' from suggestion strip", "the" in candidates)
    }
}
