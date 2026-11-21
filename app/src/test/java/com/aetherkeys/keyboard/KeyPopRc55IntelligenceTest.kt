package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPopRc55IntelligenceTest {
    @Test fun correctsImContraction() {
        assertEquals("I'm", KeyPopTypingIntelligence.correction(0, "im"))
    }

    @Test fun keepsContextualFollowUpOptions() {
        val suggestions = KeyPopTypingIntelligence.nextWords(
            0, "posted", "keep me posted"
        )
        assertTrue("Expected 'on' in contextual candidates: $suggestions", "on" in suggestions)
        assertTrue("Expected 'regarding' in contextual candidates: $suggestions", "regarding" in suggestions)
    }

    @Test fun doesNotCorrectNonEnglishContraction() {
        assertEquals(null, KeyPopTypingIntelligence.correction(1, "im"))
    }
}
