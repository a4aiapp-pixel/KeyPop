package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopRC54ContextualPersonalizationTest {
    @Test fun stronglyLearnedContextBeatsGeneralPrior() {
        val context="Kindly review the audit "
        val result=KeyPopUnifiedPrediction.rank(
            0,"",context,listOf("documentation"),listOf("report","file"),
            limit=3,strongPersonal=setOf("documentation"))
        assertEquals("documentation",result.first())
    }

    @Test fun unverifiedGlobalVocabularyGetsNoExtraBoost() {
        val context="These reports are "
        val unrelated=KeyPopUnifiedPrediction.rank(
            0,"",context,listOf("pineapple"),emptyList(),limit=6)
        val contextual=KeyPopUnifiedPrediction.rank(
            0,"",context,listOf("pineapple"),emptyList(),limit=6,
            strongPersonal=emptySet())
        assertEquals(unrelated,contextual)
        // Global personal words receive only the ordinary personal bonus;
        // they must not acquire a context-specific boost.
    }

    @Test fun reusableInformingVsUpdatingFramesApplyToUnseenRecipients() {
        val informed=KeyPopLinguisticContinuations.ranked(
            0,"Please keep Alex informed ").map{it.word}
        assertEquals("about",informed.first())
        val updated=KeyPopLinguisticContinuations.ranked(
            0,"Kindly keep the team updated ").map{it.word}
        assertEquals("on",updated.first())
    }

    @Test fun courteousModalFramesHoldAcrossUnknownClauses() {
        assertEquals("please",KeyPopLinguisticContinuations.ranked(
            0,"It would help if you could ").first().word)
        assertEquals("check",KeyPopLinguisticContinuations.ranked(
            0,"Could you please ").first().word)
    }
}
