package com.aetherkeys.keyboard

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

/** Regression for first-ever letter and independent user toggles. */
class KeyPopRC48InitialCapitalizationTest {
    private val ordinary=InputType.TYPE_CLASS_TEXT
    private val chromeSearch=ordinary or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

    @Test fun freshFieldStartsUppercaseEvenWhenHostContextIsNotReady() {
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"",true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(chromeSearch,"",true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(chromeSearch,null,true,0,0))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(ordinary,null,true,-1,-1))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(ordinary,null,true,3,3))
    }

    @Test fun firstWordAndLaterSentenceSettingsAreIndependent() {
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"",true,0,0,false))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"",false,0,0,true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"Hello. ",false,6,6,true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"Hello! ",false,6,6,true))
        assertTrue(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"Hello\n",false,6,6,true))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"Hello. ",true,6,6,false))
        assertFalse(KeyPopTypingRules.shouldAutoCapitalize(ordinary,"Hello there ",true,12,12,true))
    }

    @Test fun passwordsEmailUrlsNumbersNeverAutoCapitalize() {
        for(type in listOf(
            ordinary or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            ordinary or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            ordinary or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ordinary or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_NUMBER)) {
            assertFalse(KeyPopTypingRules.shouldAutoCapitalize(type,"",true,0,0,true))
            assertFalse(KeyPopTypingRules.shouldAutoCapitalize(type,"Text. ",true,6,6,true))
        }
    }
}
