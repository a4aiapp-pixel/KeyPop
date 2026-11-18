package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopAdaptivePlannerTest {
    @Test fun suppressStaleWordInsertion() {
        assertNull(KeyPopAdaptiveCandidatePlanner.makePlan("You a", "rest", "are"))
    }

    @Test fun replaceOnlyFragmentAtCaret() {
        val plan = KeyPopAdaptiveCandidatePlanner.makePlan("you are allow", "", "allowed")!!
        assertEquals(5, plan.replaceBeforeChars)
        assertEquals("allowed", plan.insert)
    }

    @Test fun insertLearnedAddressAfterSpace() {
        val plan = KeyPopAdaptiveCandidatePlanner.makePlan("house no ", "", "169, street no 9")!!
        assertEquals(0, plan.replaceBeforeChars)
    }

    @Test fun disallowUnexpectedFragmentOrNewline() {
        assertNull(KeyPopAdaptiveCandidatePlanner.makePlan("you are all", "", "working"))
        assertNull(KeyPopAdaptiveCandidatePlanner.makePlan("you a", "", "are\nsomething"))
    }

    @Test fun retainBestCaseInsensitiveSuggestion() {
        val model = KeyPopAdaptiveLanguageModel()
        val adaptive = model.predict("You a", personalLearningEnabled = false,
            sensitiveEditor = false)
        val existing = listOf(KeyPopAdaptiveCandidatePlanner.RankedCandidate(
            "ARE", "ARE", 100, "legacy"))
        val result = KeyPopAdaptiveCandidatePlanner.combine(adaptive, existing)
        assertEquals(1, result.count { it.replacement.equals("are", true) })
        assertEquals("adaptive:GRAMMAR", result.first().source)
    }

    @Test fun incognitoAndExcludedAppsDisablePersonalButNotGrammar() {
        val consent = KeyPopAdaptiveEditorPolicy.Consent(true, true, true)
        for (field in listOf(
            KeyPopAdaptiveEditorPolicy.Context(incognito = true),
            KeyPopAdaptiveEditorPolicy.Context(appExcluded = true))) {
            val decision = KeyPopAdaptiveEditorPolicy.decide(field, consent)
            assertTrue(decision.mayPredictGeneric)
            assertFalse(decision.maySuggestPersonal)
            assertFalse(decision.mayRememberAddresses)
        }
    }

    @Test fun urlFieldsSuppressGrammarAndPersonalSuggestions() {
        val d = KeyPopAdaptiveEditorPolicy.decide(
            KeyPopAdaptiveEditorPolicy.Context(urlOrEmail = true),
            KeyPopAdaptiveEditorPolicy.Consent(true, true, true))
        assertFalse(d.mayPredictGeneric)
        assertFalse(d.maySuggestPersonal)
        assertFalse(d.mayRememberPhrases)
    }

    @Test fun passwordDisablesPredictionAndLearning() {
        val d = KeyPopAdaptiveEditorPolicy.decide(
            KeyPopAdaptiveEditorPolicy.Context(sensitive = true),
            KeyPopAdaptiveEditorPolicy.Consent(true, true, true))
        assertFalse(d.mayPredictGeneric)
        assertFalse(d.mayRememberPhrases)
    }
}
