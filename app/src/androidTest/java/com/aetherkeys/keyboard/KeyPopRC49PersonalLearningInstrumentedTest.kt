package com.aetherkeys.keyboard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real AndroidKeyStore + SQLite checks: no fake in-memory learning. */
@RunWith(AndroidJUnit4::class)
class KeyPopRC49PersonalLearningInstrumentedTest {
    private val context:Context get()=
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun firstWordFrequencySurvivesNewKeyboardModelInstance() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val oldWords=prefs.all["learningEnabled"]
        val oldHistory=prefs.all["personalHistoryLearning"]
        prefs.edit().putBoolean("learningEnabled",true)
            .putBoolean("personalHistoryLearning",true).commit()
        val model=KeyPopPersonalLanguageModel(context)
        model.clear()
        try {
            repeat(4){model.observe(0,"","deliverables")}
            model.observe(0,"","delivery")
            val reopened=KeyPopPersonalLanguageModel(context)
            val words=reopened.candidates(0,"","deli",8)
            assertEquals("deliverables",words.first())
            assertTrue(words.contains("delivery"))
            assertTrue(reopened.candidates(0,"","deli",8).isNotEmpty())
            assertTrue(reopened.candidates(1,"","deli",8).isEmpty())
        } finally {
            model.clear()
            val edit=prefs.edit()
            if(oldWords is Boolean)edit.putBoolean("learningEnabled",oldWords)
                else edit.remove("learningEnabled")
            if(oldHistory is Boolean)edit.putBoolean("personalHistoryLearning",oldHistory)
                else edit.remove("personalHistoryLearning")
            edit.commit()
        }
    }

    @Test fun nextWordLearningHandlesIAndExplicitLineBreak() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val oldWords=prefs.all["learningEnabled"]
        val oldHistory=prefs.all["personalHistoryLearning"]
        prefs.edit().putBoolean("learningEnabled",true)
            .putBoolean("personalHistoryLearning",true).commit()
        val model=KeyPopPersonalLanguageModel(context)
        model.clear()
        try {
            repeat(3){model.observe(0,"I ","am")}
            assertEquals("am",model.candidates(0,"I ","",5).first())
            assertTrue(model.canLearn("Prior text\\nI am ","working"))
            assertFalse(model.canLearn("Somebody@example.com ","name"))
            assertFalse(model.canLearn("My account 012345 ","number"))
            val newModel=KeyPopPersonalLanguageModel(context)
            assertEquals("am",newModel.candidates(0,"I ","",5).first())
        } finally {
            model.clear()
            val edit=prefs.edit()
            if(oldWords is Boolean)edit.putBoolean("learningEnabled",oldWords)
                else edit.remove("learningEnabled")
            if(oldHistory is Boolean)edit.putBoolean("personalHistoryLearning",oldHistory)
                else edit.remove("personalHistoryLearning")
            edit.commit()
        }
    }
}
