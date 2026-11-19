package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/**
 * Offline ranking/selection regressions, not a claimed Gboard benchmark.
 * All fixtures use static local examples; no user typing is transmitted.
 */
class KeyPopRC46ContextAndSelectionTest {
    @Test fun deepPhraseContextBeatsGenericDictionaryOrder() {
        val next=KeyPopUnifiedPrediction.rank(0,"","Please confirm the ",
            emptyList(),listOf("person","meeting","details"),limit=3)
        assertEquals("details",next.first())
        val continuation=KeyPopUnifiedPrediction.rank(0,"","Thank you for ",
            emptyList(),listOf("these","some","your"),limit=3)
        assertEquals("your",continuation.first())
    }

    @Test fun contextCanCompletePartialWordBeforeDictionaryFallback() {
        val options=KeyPopUnifiedPrediction.rank(0,"pro","Please pro",
            emptyList(),listOf("project","process","provide"),limit=5)
        assertEquals("provide",options.first())
    }

    @Test fun agreementFiltersObviouslyWrongEnglishAuxiliaries() {
        val first=KeyPopUnifiedPrediction.rank(0,"","I ",
            emptyList(),listOf("is","are","am","have"),limit=8)
        assertFalse(first.contains("is"))
        assertFalse(first.contains("are"))
        assertTrue(first.take(3).contains("am"))
        val she=KeyPopUnifiedPrediction.rank(0,"","She ",
            emptyList(),listOf("are","have","is","has"),limit=8)
        assertFalse(she.contains("are"))
        assertFalse(she.contains("have"))
        assertTrue(she.contains("is"))
        assertTrue(KeyPopGrammarAgreement.score("If I ","","were")>0)
    }

    @Test fun personalContextWinsOverGenericSingleWordPhrasePrior() {
        val choices=KeyPopUnifiedPrediction.rank(0,"","Please ",
            listOf("confirm"),listOf("check","send"),limit=3)
        assertEquals("confirm",choices.first())
    }

    @Test fun rejectTwiceKeepsBadCorrectionOutOfContextSuggestions() {
        val list=KeyPopUnifiedPrediction.rank(0,"tge","I typed tge",
            emptyList(),listOf("the","tie"),"the",
            rejected=mapOf("the" to 3),limit=8)
        assertFalse(list.contains("the"))
    }

    @Test fun urduSindhiKeepNativeScriptContext() {
        val urdu=KeyPopContextPhraseModel.ranked(1,"براہ کرم ")
            .map{it.word}
        assertTrue("Urdu phrase prior is missing",urdu.isNotEmpty())
        val sindhi=KeyPopContextPhraseModel.ranked(2,"مهرباني ڪري ")
            .map{it.word}
        assertTrue("Sindhi phrase prior is missing",sindhi.isNotEmpty())
        assertTrue(sindhi.any{it.any {character -> character.code>127}})
    }

    @Test fun disablingEnhancedRankingRemainsBackwardCompatible() {
        val words=KeyPopUnifiedPrediction.rank(0,"","I ",
            emptyList(),listOf("are","am","is"),limit=8,enhancedContext=false)
        assertTrue(words.containsAll(listOf("are","am","is")))
    }

    @Test fun selectionHighlightDefaultsAreDarkAndOpacityIsClamped() {
        val dark=KeyPopSelectionStyle.withOpacity(
            KeyPopSelectionStyle.DEFAULT_COLOR,KeyPopSelectionStyle.DEFAULT_OPACITY)
        assertEquals(0x195A91,dark and 0xFFFFFF)
        assertTrue((dark ushr 24)>200)
        assertEquals(255,KeyPopSelectionStyle.withOpacity(dark,120) ushr 24)
        assertEquals(64,KeyPopSelectionStyle.withOpacity(dark,0) ushr 24)
    }
}
