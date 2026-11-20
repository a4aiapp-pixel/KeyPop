package com.aetherkeys.keyboard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Check the real loaded SCOWL dictionary, persisted AES-encrypted n-grams and
 * live correction service logic, rather than only pure stub functions.
 */
@RunWith(AndroidJUnit4::class)
class KeyPopRC52IntelligenceInstrumentedTest {
    private val context:Context
        get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun contractionsAndRealDictionaryCandidatesWorkOffline() {
        val lexicon=KeyPopLexicon(context)
        lexicon.warmup()
        assertEquals("I'm",lexicon.fastCorrection(0,"im","im"))
        assertEquals("I'm",lexicon.fastCorrection(0,"Im","Im"))
        assertEquals("I'm",lexicon.correction(0,"im","im"))
        assertTrue("Missing I'm from actual suggestion candidates",
            "I'm" in lexicon.suggestions(0,"im",8,beforeCursor="I think im"))
        assertNull("Uppercase IM should not be forced",
            lexicon.fastCorrection(0,"IM","IM"))
        val words=lexicon.nextWords(0,"posted","Keep me posted ").take(3)
        assertEquals("on",words.first())
        assertTrue("regarding" in words)
        assertTrue("about" in words)
    }

    @Test fun personalContextCanPreferRegardingOverGenericOn() {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val oldLearn=prefs.all["learningEnabled"]
        val oldPersonal=prefs.all["personalHistoryLearning"]
        prefs.edit().putBoolean("learningEnabled",true)
            .putBoolean("personalHistoryLearning",true).commit()
        val model=KeyPopPersonalLanguageModel(context)
        try {
            model.clear()
            repeat(3) {
                model.observe(0,"Keep me posted ","regarding")
            }
            val remembered=model.candidates(0,"Keep me posted ","",5)
            assertTrue("Personal phrase was not retained",remembered.contains("regarding"))
            val ranked=KeyPopUnifiedPrediction.rank(0,"","Keep me posted ",
                remembered,emptyList(),limit=3)
            assertEquals("regarding",ranked.first())
            assertTrue(ranked.contains("on"))
        } finally {
            model.clear()
            val edit=prefs.edit()
            if(oldLearn is Boolean)edit.putBoolean("learningEnabled",oldLearn)
                else edit.remove("learningEnabled")
            if(oldPersonal is Boolean)
                edit.putBoolean("personalHistoryLearning",oldPersonal)
            else edit.remove("personalHistoryLearning")
            edit.commit()
        }
    }
}
