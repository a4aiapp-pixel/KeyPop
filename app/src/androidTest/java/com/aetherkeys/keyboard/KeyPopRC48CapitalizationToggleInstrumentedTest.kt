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

/** Checks that both live Typing switches exist, work and persist independently. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC48CapitalizationToggleInstrumentedTest {
    @Test fun firstLetterAndSentenceSwitchesAreVisibleAndIndependent() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val oldFirst=prefs.getBoolean("autoCap",true)
        val oldSentence=prefs.getBoolean("autoCapSentences",true)
        prefs.edit().putBoolean("autoCap",true).putBoolean("autoCapSentences",true).commit()
        try {
            val intent=Intent(context,MainActivity::class.java)
                .putExtra("openSection","typing")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    fun find(node:View,label:String):Switch? {
                        if(node is Switch && node.contentDescription==label)return node
                        if(node is ViewGroup)for(i in 0 until node.childCount) {
                            find(node.getChildAt(i),label)?.let { return it }
                        }
                        return null
                    }
                    val first=find(activity.window.decorView,"Capitalize the very first letter")
                    val sentences=find(activity.window.decorView,
                        "Capitalize after sentences and line breaks")
                    assertNotNull("Missing first-letter setting",first)
                    assertNotNull("Missing sentence capitalization setting",sentences)
                    assertTrue(first!!.isChecked)
                    assertTrue(sentences!!.isChecked)
                    first.performClick()
                    assertFalse(prefs.getBoolean("autoCap",true))
                    assertTrue(prefs.getBoolean("autoCapSentences",false))
                    sentences.performClick()
                    assertFalse(prefs.getBoolean("autoCapSentences",true))
                    first.performClick()
                    assertTrue(prefs.getBoolean("autoCap",false))
                }
            }
        } finally {
            prefs.edit().putBoolean("autoCap",oldFirst)
                .putBoolean("autoCapSentences",oldSentence).commit()
        }
    }
}
