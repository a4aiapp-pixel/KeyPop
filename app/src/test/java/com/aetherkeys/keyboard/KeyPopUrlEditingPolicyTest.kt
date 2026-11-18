package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Pure regression suite: URL entry must never be modified by the prediction engine. */
class KeyPopUrlEditingPolicyTest {
    @Test fun urlFieldsAreProtectedBeforeTheFirstCharacter() {
        val uri = KeyPopUrlEditingPolicy.decide(true, false, "")
        val email = KeyPopUrlEditingPolicy.decide(false, true, "user")
        assertTrue(uri.protected)
        assertFalse(uri.allowAutomaticSpacing)
        assertFalse(uri.allowAutomaticCorrection)
        assertFalse(uri.allowPersonalLearning)
        assertTrue(email.protected)
        assertFalse(email.allowSuggestions)
    }

    @Test fun chromeOmniboxLikeGenericTextStillProtectsUrlTokens() {
        for ((before, after) in listOf(
            "https://exam" to "ple.com/long/path?x=1",
            "www.example.com" to "/folder?q=1",
            "user@example.com" to "",
            "192.168.1.1" to "/configuration",
            "mydomain.org" to "/abc",
            "https://" to "",
            "long/url/fragment?x=1" to "&y=2"
        )) {
            val result = KeyPopUrlEditingPolicy.decide(false, false, before, after)
            assertTrue("Should protect URL $before$after", result.protected)
            assertFalse(result.allowSuggestions)
            assertFalse(result.allowAutomaticPunctuation)
            assertNull(KeyPopAdaptiveCandidatePlanner.makePlan(before, after, "allowed"))
        }
    }

    @Test fun ordinaryWordsAndSearchPhrasesRetainSuggestions() {
        for (query in listOf("You a", "you are allow", "house no", "how are you", "hello.")) {
            val result = KeyPopUrlEditingPolicy.decide(false, false, query)
            assertFalse(query, result.protected)
            assertTrue(query, result.allowSuggestions)
        }
    }

    @Test fun earlierUrlDoesNotDisableSuggestionsForNewWord() {
        assertFalse(KeyPopUrlEditingPolicy.touchesUrlOrEmail(
            "Check https://example.com then ", "reply"))
    }
}
