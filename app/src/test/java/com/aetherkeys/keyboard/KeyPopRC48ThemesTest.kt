package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

/** Theme identities and distinctive design treatments survive upgrades. */
class KeyPopRC48ThemesTest {
    @Test fun preserveFourApprovedStylesAndReplaceLegacyPresets() {
        val skins=KeyPopSkins.all
        assertEquals(15,skins.size)
        assertEquals("AMOLED Black",skins[9].name)
        assertEquals(0xFF000000.toInt(),skins[9].background)
        assertTrue(skins[10].name.contains("Gboard"))
        assertTrue(skins[11].name.contains("SwiftKey"))
        assertTrue(skins[12].name.contains("Samsung"))
        for(removed in listOf("Retro Pixel","Zen Paper","Speed Type","Airy Comfort",
            "Sunset Ember","Orchid Bloom"))
            assertFalse(skins.any{it.name==removed})
    }
    @Test fun newThemesVaryBeyondColor() {
        val skins=KeyPopSkins.all
        assertEquals(15,skins.map{it.name}.distinct().size)
        assertTrue(skins.map{it.treatment}.distinct().size>=11)
        assertTrue(skins.map{it.font}.distinct().size>=3)
        assertTrue(skins.map{it.lift}.distinct().size>=4)
        assertTrue(skins.map{it.gap}.distinct().size>=3)
    }
}
