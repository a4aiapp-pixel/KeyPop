package com.aetherkeys.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The suggestion strip must complete unfinished words before Space is pressed. */
class KeyPopCompletionLexiconTest {
    @Test fun deliverabOffersDeliverableBeforeDeliverables() {
        val suggestions=KeyPopCompletionLexicon.candidates("deliverab")
        assertEquals("deliverable",suggestions.first())
        assertTrue(suggestions.contains("deliverables"))
    }
    @Test fun prefixCompletionIsCaseInsensitive() {
        assertEquals("deliverable",KeyPopCompletionLexicon.candidates("DELIVERAB").first())
        assertTrue(KeyPopCompletionLexicon.candidates("customiz").contains("customization"))
        assertTrue(KeyPopCompletionLexicon.candidates("implement").contains("implementation"))
    }
    @Test fun noAutoReplacementForACompleteOrInvalidWord() {
        assertFalse(KeyPopCompletionLexicon.candidates("deliverable").contains("deliverable"))
        assertTrue(KeyPopCompletionLexicon.candidates("https://").isEmpty())
        assertTrue(KeyPopCompletionLexicon.candidates("de").isEmpty())
    }
    @Test fun modernProfessionalWordsHaveValidityEntries() {
        assertTrue(KeyPopCompletionLexicon.contains("deliverable"))
        assertTrue(KeyPopCompletionLexicon.contains("deliverables"))
        assertTrue(KeyPopCompletionLexicon.contains("mistyped"))
        assertFalse(KeyPopCompletionLexicon.contains("deliverab"))
    }
}
