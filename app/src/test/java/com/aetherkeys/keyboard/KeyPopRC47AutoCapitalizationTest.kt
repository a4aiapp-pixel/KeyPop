package com.aetherkeys.keyboard

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression gates: first word must start uppercase without manual Shift. */
class KeyPopRC47AutoCapitalizationTest {
    private val text=InputType.TYPE_CLASS_TEXT
    @Test fun firstWordAndWhitespace() {
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(text,"",true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(text,"  ",true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(text,"\n",true))
    }
    @Test fun beginningOfSubsequentSentences() {
        for(before in listOf("Hello. ","What? ","Stop! ","Next\n","کیا؟ ","ٹھیک۔ ")) {
            assertTrue("After: $before",KeyPopTypingRules.shouldAutoCapitalize(text,before,true))
        }
    }
    @Test fun middleOfSentenceIsNotCapitalized() {
        for(before in listOf("Hello ","Hello there","a ", "test, ","Hi: ")) {
            assertFalse("After: $before",KeyPopTypingRules.shouldAutoCapitalize(text,before,true))
        }
    }
    @Test fun privacyAndSpecialEditorsNotChanged() {
        val blocked=listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_NUMBER)
        for(kind in blocked)assertFalse(KeyPopTypingRules.shouldAutoCapitalize(kind,"",true))
    }
    @Test fun chromeStyleNoSuggestionsStillCapitalizesFirstWord() {
        val chromeText=InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(chromeText,"",true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(chromeText,"Hello. ",true))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(chromeText,"Hello ",true))
    }
    @Test fun firstFocusNullContextOnlyUsesConfirmedEmptyField() {
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(text,null,true,0,0))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(text,null,true,4,4))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(text,null,true,-1,-1))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(text,null,false,0,0))
        val secure=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(secure,null,true,0,0))
    }
    @Test fun respectsDisabledSettingAndUnknownContext() {
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(text,"",false))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(text,null,true))
    }
}
