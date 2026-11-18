package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopAdaptiveLanguageModelTest {
    @Test fun singularVerbAgreement() {
        val m = KeyPopAdaptiveLanguageModel()
        assertEquals("goes", m.predict("he go", personalLearningEnabled = false,
            sensitiveEditor = false).first().replacement)
    }

    @Test fun addressesRequireSeparateConsent() {
        val m = KeyPopAdaptiveLanguageModel()
        val address = "house no 169, street no 9, J Sector"
        assertFalse(m.learnPhrase(address, true, false))
        assertTrue(m.learnPhrase(address, true, false, allowAddressMemory = true))
        assertTrue(m.predict("house no ", personalLearningEnabled = true,
            sensitiveEditor = false).none { it.kind.name.startsWith("PERSONAL") })
        assertTrue(m.predict("house no ", personalLearningEnabled = true,
            sensitiveEditor = false, allowAddressMemory = true)
            .any { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_PHRASE })
    }

    @Test fun grammarAre() {
        val m = KeyPopAdaptiveLanguageModel()
        assertEquals("are", m.predict("You a", personalLearningEnabled = false, sensitiveEditor = false).first().replacement)
    }

    @Test fun passiveAllowed() {
        val m = KeyPopAdaptiveLanguageModel()
        assertEquals("allowed", m.predict("you are allow", personalLearningEnabled = false, sensitiveEditor = false).first().replacement)
    }

    @Test fun personalAddressRecallFromPartialWord() {
        val m = KeyPopAdaptiveLanguageModel()
        assertTrue(m.learnPhrase("house no 169, street no 9, J Sector", true, false, allowAddressMemory = true))
        val predicted = m.predict("house no", personalLearningEnabled = true, sensitiveEditor = false, allowAddressMemory = true)
        assertTrue(predicted.any { it.replacement.startsWith("no 169, street no 9") })
    }

    @Test fun personalAddressRecallAtWordBoundary() {
        val m = KeyPopAdaptiveLanguageModel()
        assertTrue(m.learnPhrase("house no 169, street no 9, J Sector", true, false, allowAddressMemory = true))
        val predicted = m.predict("house no ", personalLearningEnabled = true, sensitiveEditor = false, allowAddressMemory = true)
        assertTrue(predicted.any { it.replacement.startsWith("169, street no 9") })
    }

    @Test fun repeatedPhraseWinsAndPersists() {
        var time = 42_000L
        val m = KeyPopAdaptiveLanguageModel({ time })
        m.learnPhrase("house no 169, street no 9, J Sector", true, false, allowAddressMemory = true)
        time += 1000L
        m.learnPhrase("house no 169, street no 9, J Sector", true, false, allowAddressMemory = true)
        assertEquals(2, m.snapshot().single().uses)
        val next = KeyPopAdaptiveLanguageModel({ time })
        next.restore(m.snapshot())
        assertTrue(next.predict("house no ", personalLearningEnabled = true, sensitiveEditor = false, allowAddressMemory = true)
            .any { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_PHRASE })
    }

    @Test fun noLearningWithoutConsentAndInSensitiveFields() {
        val m = KeyPopAdaptiveLanguageModel()
        assertFalse(m.learnPhrase("house no 169, street no 9, J Sector", false, false))
        assertFalse(m.learnPhrase("house no 169, street no 9, J Sector", true, true))
        assertTrue(m.snapshot().isEmpty())
        m.learnPhrase("house no 169, street no 9, J Sector", true, false, allowAddressMemory = true)
        assertFalse(m.predict("house no ", personalLearningEnabled = false, sensitiveEditor = false)
            .any { it.kind.name.startsWith("PERSONAL") })
        assertTrue(m.predict("house no ", personalLearningEnabled = true, sensitiveEditor = true).isEmpty())
    }

    @Test fun neverStorePasswordsPinsLinksOrCardNumbers() {
        val m = KeyPopAdaptiveLanguageModel()
        for (text in listOf("my password is hunter2", "verification code 123456 please use it",
            "send to https://example.com now", "email me at user@example.com later",
            "card 4111 1111 1111 1111 please")) {
            assertFalse(text, m.learnPhrase(text, true, false))
        }
    }

    @Test fun previouslyChosenPersonalWordsAreRankedAndRemembered() {
        val m = KeyPopAdaptiveLanguageModel()
        repeat(3) { assertTrue(m.learnWord("internationalization", true, false)) }
        assertFalse(m.learnWord("internationalization", true, true))
        assertTrue(m.predict("inter", personalLearningEnabled=true, sensitiveEditor=false)
            .any { it.replacement == "internationalization" &&
                it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_WORD })
        val restored = KeyPopAdaptiveLanguageModel()
        restored.restoreWords(m.wordSnapshot())
        assertEquals(3, restored.wordSnapshot().single().uses)
        assertTrue(restored.predict("inter", personalLearningEnabled=true, sensitiveEditor=false)
            .any { it.replacement == "internationalization" })
        assertTrue(restored.predict("inter", personalLearningEnabled=false, sensitiveEditor=false)
            .none { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_WORD })
    }

    @Test fun eraseOneOrAll() {
        val m = KeyPopAdaptiveLanguageModel()
        val address = "house no 169, street no 9, J Sector"
        m.learnPhrase(address, true, false, allowAddressMemory = true)
        assertTrue(m.deletePhrase(address.uppercase()))
        assertTrue(m.snapshot().isEmpty())
        m.learnPhrase(address, true, false, allowAddressMemory = true)
        m.clear()
        assertTrue(m.snapshot().isEmpty())
    }

    @Test fun phraseRecallDoesNotInsertInsideExistingToken() {
        val m = KeyPopAdaptiveLanguageModel()
        m.learnPhrase("house no 169, street no 9, J Sector", true, false, allowAddressMemory = true)
        assertTrue(m.predict("house no", "continuation", true, false, allowAddressMemory = true)
            .none { it.kind == KeyPopAdaptiveLanguageModel.Kind.PERSONAL_PHRASE })
    }

    @Test fun capMemoryWithoutGlobalDataRetention() {
        val m = KeyPopAdaptiveLanguageModel(maxRecords=2)
        for (i in 1..4) m.learnPhrase("common example phrase number $i for routine work", true, false)
        assertEquals(2, m.snapshot().size)
    }
}
