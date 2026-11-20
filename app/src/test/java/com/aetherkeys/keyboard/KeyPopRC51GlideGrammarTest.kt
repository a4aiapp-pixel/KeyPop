package com.aetherkeys.keyboard

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

/** Offline gesture and grammar acceptance cases, independent of visual skin. */
class KeyPopRC51GlideGrammarTest {
    private val plain=InputType.TYPE_CLASS_TEXT

    @Test fun glideToggleAppliesToAllNativeKeyboardsAndCustomAlphabeticKeys() {
        for((lang,key) in listOf(0 to "q",1 to "س",2 to "ڌ")) {
            assertTrue("$lang / $key",KeyPopGlidePolicy.enabled(
                true,lang,key,false,false,false,plain))
            assertFalse(KeyPopGlidePolicy.enabled(
                false,lang,key,false,false,false,plain))
        }
        for(key in listOf("a","z","q","k","e")) {
            assertTrue(KeyPopGlidePolicy.enabled(true,0,key,
                false,false,false,plain))
        }
    }

    @Test fun safeguardsPreventGesturesOnPrivateAndNonTextFields() {
        val blocked=listOf(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            plain or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            plain or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            plain or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            plain or InputType.TYPE_TEXT_VARIATION_URI)
        for(kind in blocked)assertFalse("editor=$kind",KeyPopGlidePolicy.enabled(
            true,0,"a",false,false,false,kind))
        assertFalse(KeyPopGlidePolicy.enabled(true,0,"a",true,false,false,plain))
        assertFalse(KeyPopGlidePolicy.enabled(true,0,"a",false,true,false,plain))
        assertFalse(KeyPopGlidePolicy.enabled(true,0,"a",false,false,true,plain))
        assertFalse(KeyPopGlidePolicy.enabled(true,0,"SPACE",false,false,false,plain))
    }

    @Test fun decoderHandlesRepeatsAndMissedAndExtraSamples() {
        val words=listOf("hello","help","helmet","hero")
        assertEquals("hello",KeyPopGlideScoring.rank(
            listOf("h","e","l","o"),words).first())
        assertEquals("hello",KeyPopGlideScoring.rank(
            listOf("h","e","y","l","o"),words).first())
        assertEquals("hello",KeyPopGlideScoring.rank(
            listOf("h","e","o"),listOf("hello")).first())
        assertEquals("keyboard",KeyPopGlideScoring.rank(
            listOf("k","e","y","b","o","a","r","d"),
            listOf("keyboard","keypad")).first())
    }

    @Test fun nativeScriptPathsDecodeWithoutTransliterating() {
        assertEquals("سلام",KeyPopGlideScoring.rank(
            listOf("س","ل","ا","م"),listOf("سلام","شکریہ")).first())
        assertEquals("سنڌي",KeyPopGlideScoring.rank(
            listOf("س","ن","ڌ","ي"),listOf("سنڌي","سنڌ")).first())
    }

    @Test fun unintendedSwipesAndUnrelatedWordsAreNotInserted() {
        assertTrue(KeyPopGlideScoring.rank(listOf("h","e"),
            listOf("hello")).isEmpty())
        assertTrue(KeyPopGlideScoring.rank(listOf("a","b","c"),
            listOf("hello","please")).isEmpty())
        assertTrue(KeyPopGlideScoring.rank(listOf("a","b","c"),
            listOf("apple","abcde")).isEmpty())
    }

    @Test fun grammarCoversCommonAgreementAndArticleCases() {
        val samples=mapOf(
            "He go" to "He goes",
            "She do" to "She does",
            "I does" to "I do",
            "They goes" to "They go",
            "I doesn't" to "I don't",
            "Did went" to "Did go",
            "She has went" to "She has gone",
            "a hour" to "an hour",
            "an university" to "a university",
            "an user" to "a user")
        for((typed,expected) in samples) {
            val fix=KeyPopGrammarRules.suggest(typed,0)
            assertNotNull("Missing fix for $typed",fix)
            assertEquals(typed,expected,typed.dropLast(fix!!.remove)+fix.replacement)
        }
    }

    @Test fun questionAuxiliariesDoNotTriggerFalseGrammarCorrections() {
        for(valid in listOf("Does he have","Did she go","Can he do",
            "Could she have","Does he go","A good meeting")) {
            assertNull(valid,KeyPopGrammarRules.suggest(valid,0))
        }
        assertNull(KeyPopGrammarRules.suggest("He go",1))
    }
}
