package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopRC44FormFieldPolicyTest {
    @Test fun standardAndroidHintsRecognized() {
        assertEquals("name", KeyPopFormFields.identify(listOf("personName"), ""))
        assertEquals("givenName", KeyPopFormFields.identify(listOf("givenName"), ""))
        assertEquals("familyName", KeyPopFormFields.identify(listOf("familyName"), ""))
        assertEquals("email", KeyPopFormFields.identify(listOf("emailAddress"), ""))
        assertEquals("phone", KeyPopFormFields.identify(listOf("phoneNumber"), ""))
        assertEquals("streetAddress", KeyPopFormFields.identify(listOf("postalAddress"), ""))
        assertEquals("postalCode", KeyPopFormFields.identify(listOf("postalCode"), ""))
    }

    @Test fun formVaultNeverTriesToFillSensitiveUnrelatedFields() {
        for (hint in listOf("password", "newPassword", "creditCardNumber",
                "creditCardSecurityCode", "smsOTPCode", "username", "unknown")) {
            assertNull("hint $hint must be excluded",KeyPopFormFields.identify(listOf(hint), "email"))
        }
        for (clue in listOf("password", "yourPIN", "payment_card_number",
                "cvv", "securityCode", "verificationToken", "otpCode")) {
            assertNull("clue $clue must be excluded",KeyPopFormFields.identify(emptyList(), clue))
        }
    }

    @Test fun formSanitizationDoesNotStorePasswordsOrUnknownData() {
        val safe = KeyPopFormFields.sanitize(mapOf(
            "name" to " User Name ", "email" to "name@example.com",
            "password" to "NOT-A-PASSWORD", "bankAccount" to "SHOULD-NOT-BE-SAVED"))
        assertEquals("User Name",safe["name"])
        assertEquals("name@example.com",safe["email"])
        assertEquals(2,safe.size)
    }
}
