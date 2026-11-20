package com.aetherkeys.keyboard

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real device/emulator: toggle persists and all language indexes decode. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC51GlideInstrumentedTest {
    @Test fun glideSwitchIsVisibleAndPersistsWithoutChangingThemes() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val old=prefs.all["glide"]
        val oldTrail=prefs.all["glideTrailVisible"]
        val currentTheme=prefs.getInt("theme",0)
        prefs.edit().putBoolean("glide",true)
            .putBoolean("glideTrailVisible",true).commit()
        try {
            val intent=Intent(ctx,MainActivity::class.java)
                .putExtra("openSection","typing")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    fun find(node:View,description:String):Switch? {
                        if(node is Switch && node.contentDescription==description)return node
                        if(node is ViewGroup)
                            for(i in 0 until node.childCount)
                                find(node.getChildAt(i),description)?.let{return it}
                        return null
                    }
                    val glide=find(activity.window.decorView,
                        "Glide typing — swipe across letters (all layouts and themes)")
                    val trail=find(activity.window.decorView,"Show colored glide trail")
                    assertNotNull("Missing universal swipe toggle",glide)
                    assertNotNull("Missing optional trail switch",trail)
                    assertTrue(glide!!.isChecked)
                    glide.performClick()
                    assertFalse(prefs.getBoolean("glide",true))
                    assertEquals(currentTheme,prefs.getInt("theme",0))
                    glide.performClick()
                    assertTrue(prefs.getBoolean("glide",false))
                    trail!!.performClick()
                    assertFalse(prefs.getBoolean("glideTrailVisible",true))
                }
            }
        }finally {
            val edit=prefs.edit()
            if(old is Boolean)edit.putBoolean("glide",old) else edit.remove("glide")
            if(oldTrail is Boolean)edit.putBoolean("glideTrailVisible",oldTrail)
                else edit.remove("glideTrailVisible")
            edit.commit()
        }
    }

    @Test fun actualLocalDictionaryRecognizesEnglishUrduAndSindhiPaths() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val lexicon=KeyPopLexicon(ctx)
        lexicon.warmup()
        assertEquals("hello",lexicon.glide(0,listOf("h","e","l","o")))
        assertEquals("سلام",lexicon.glide(1,listOf("س","ل","ا","م")))
        assertEquals("سنڌي",lexicon.glide(2,listOf("س","ن","ڌ","ي")))
    }
}
