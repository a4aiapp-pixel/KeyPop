package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Generalized QWERTY digit-row slip detection, not just a canned typo table. */
class KeyPopRC49KeyboardNoiseTest {
    @Test fun repairInteriorDigitFromQwertyNeighbor() {
        assertEquals("does",KeyPopDigitSlipRules.repairCandidate("Do3s"))
        assertEquals("good",KeyPopDigitSlipRules.repairCandidate("go9d"))
        assertEquals("word",KeyPopDigitSlipRules.repairCandidate("wo4d"))
        assertEquals("typed",KeyPopDigitSlipRules.repairCandidate("t6ped"))
    }
    @Test fun doNotRewriteNumbersPasswordsOrAlphanumericIdentifiers() {
        for(input in listOf("123", "A52", "3G", "2026", "Q12w", "do33s",
            "2FA", "version2", "myemail@example.com", "http://site.com")) {
            assertNull(input,KeyPopDigitSlipRules.repairCandidate(input))
        }
    }
    @Test fun privateWordModelRanksPersonalNextWordAboveGenericChoices() {
        val suggestions=KeyPopUnifiedPrediction.rank(0,"","",
            listOf("deliverables","documents"),listOf("the","have"),limit=5)
        assertEquals("deliverables",suggestions.first())
    }
}
