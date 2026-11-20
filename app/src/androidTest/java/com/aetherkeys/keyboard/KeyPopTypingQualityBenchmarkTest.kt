package com.aetherkeys.keyboard

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Baseline *engine-level* measurements, not real-app/Gboard head-to-head tests.
 * Holdout cases are never bundled with production model data. */
@RunWith(AndroidJUnit4::class)
class KeyPopTypingQualityBenchmarkTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val lexicon by lazy {KeyPopLexicon(instrument.targetContext).also{it.warmup()}}
    private fun normal(s:String)=s.lowercase(Locale.ROOT).replace('’','\'')

    private fun record(group:String,total:Int,top1:Int,top3:Int,
                       misses:List<String>,durations:List<Long>) {
        val sorted=durations.sorted()
        val p95=if(sorted.isEmpty())0L else sorted[((sorted.size*95+99)/100-1)
            .coerceIn(0,sorted.lastIndex)]
        val line="KP_QUALITY|group="+group+"|cases="+total+"|top1="+top1+
            "|top3="+top3+"|p95EngineMs="+(p95/1000000.0)+"|misses="+
            misses.take(20).joinToString(";"){it.replace("|","_")}
        instrument.sendStatus(0,Bundle().apply{putString("stream",line+"\n")})
    }

    private fun spelling(typed:String,context:String):List<String> {
        val before=context+typed
        val dictionary=lexicon.suggestions(0,typed,8,beforeCursor=before)
        val correction=lexicon.correction(0,typed,before)
        return KeyPopUnifiedPrediction.rank(0,typed,before,emptyList(),
            dictionary,correction,limit=8).map(::normal)
    }

    private fun next(before:String):List<String> {
        val previous=KeyPopUnicodeWords.tokens(before).lastOrNull().orEmpty()
        return KeyPopUnifiedPrediction.rank(0,"",before,emptyList(),
            lexicon.nextWords(0,previous,before),limit=8).map(::normal)
    }

    private fun scoreSpelling(group:String,cases:List<Pair<String,String>>,
                              context:String) {
        var top1=0;var top3=0
        val misses=ArrayList<String>();val timings=ArrayList<Long>()
        for((typed,expected) in cases) {
            val start=SystemClock.elapsedRealtimeNanos()
            val words=spelling(typed,context)
            timings.add(SystemClock.elapsedRealtimeNanos()-start)
            val target=normal(expected)
            if(words.firstOrNull()==target)top1++
            if(target in words.take(3))top3++ else misses.add(typed+">"+expected)
        }
        record(group,cases.size,top1,top3,misses,timings)
    }

    @Test fun unseenMisspellingsHaveMeasuredTopOneAndTopThreeRecall() {
        val cases=listOf(
            "enviroment" to "environment",
            "assessmnt" to "assessment",
            "responsibilty" to "responsibility",
            "accomodation" to "accommodation",
            "consistant" to "consistent",
            "analyzis" to "analysis",
            "commnication" to "communication",
            "persisent" to "persistent",
            "expctation" to "expectation",
            "apropriate" to "appropriate",
            "accross" to "across",
            "concensus" to "consensus",
            "implemntation" to "implementation",
            "maintanance" to "maintenance",
            "qusetionnaire" to "questionnaire",
            "suggstion" to "suggestion",
            "realiable" to "reliable",
            "recomendation" to "recommendation",
            "develpoment" to "development",
            "confidnce" to "confidence",
            "accurcy" to "accuracy",
            "fuctionality" to "functionality",
            "continous" to "continuous",
            "committment" to "commitment",
            "confrmation" to "confirmation",
            "technolgy" to "technology",
            "considred" to "considered",
            "presrvation" to "preservation",
            "refrence" to "reference",
            "reconcilliation" to "reconciliation",
            "descripton" to "description",
            "distributon" to "distribution",
            "optimzation" to "optimization",
            "professionl" to "professional",
            "succesful" to "successful",
            "instalation" to "installation",
            "interupted" to "interrupted",
            "delivry" to "delivery",
            "excessve" to "excessive",
            "securty" to "security")
        scoreSpelling("SPELL_HOLDOUT",cases,"Please review the ")
    }

    @Test fun userReportedTyposRemainSeparateFromHeldoutCases() {
        val cases=listOf(
            "im" to "I'm","Im" to "I'm","Do3s" to "does",
            "te" to "the","thmes" to "themes",
            "miatyped" to "mistyped","googke" to "google",
            "googlr" to "google","inteligence" to "intelligence",
            "custmization" to "customization","samsng" to "samsung",
            "microsfot" to "microsoft")
        scoreSpelling("SPELL_REGRESSION",cases,"Please check ")
    }

    @Test fun multiwordNextPredictionRecallMeasuresAcceptableAlternatives() {
        val cases=listOf(
            "Please keep the manager posted " to "on about regarding",
            "Keep us informed " to "about of regarding",
            "Please update me " to "on about regarding",
            "We are looking forward to " to "hearing seeing working",
            "Thank you for " to "your the helping",
            "Please let me " to "know check see",
            "They " to "are have will",
            "He " to "is has will",
            "I " to "am have will",
            "If you could " to "please send help",
            "The investigation remains " to "open ongoing under",
            "You may need to " to "check review update",
            "A key feature of " to "the this our",
            "Please confirm whether " to "the you it",
            "It appears that " to "the it this",
            "The report was " to "submitted prepared received",
            "We appreciate your " to "support cooperation feedback",
            "Would you please " to "check send confirm",
            "For more information " to "please about contact",
            "Could you provide " to "the more a",
            "The following items " to "are were have",
            "Can we proceed " to "with to now",
            "This option should " to "be work allow",
            "We must ensure " to "that the all")
        var top1=0;var top3=0
        val timings=ArrayList<Long>();val misses=ArrayList<String>()
        for((context,choices) in cases) {
            val acceptable=choices.split(' ').toSet()
            val start=SystemClock.elapsedRealtimeNanos()
            val offers=next(context)
            timings.add(SystemClock.elapsedRealtimeNanos()-start)
            if(offers.firstOrNull() in acceptable)top1++
            if(offers.take(3).any{it in acceptable})top3++
            else misses.add(context.trim())
        }
        record("NEXT_HOLDOUT",cases.size,top1,top3,misses,timings)
    }

    @Test fun grammarReportsTrueCorrectionsAndFalseEditsSeparately() {
        val wrong=listOf(
            "I are" to "I am","She have" to "She has",
            "They is" to "They are","We was" to "We were",
            "He go" to "He goes","She do" to "She does",
            "I does" to "I do","You goes" to "You go",
            "Did went" to "Did go","a hour" to "an hour",
            "an university" to "a university","Im" to "I'm",
            "we doesnt" to "we don't","they doesnt" to "they don't",
            "we has" to "we have","I have went" to "I have gone")
        val valid=listOf("I am","He has","She goes","They are",
            "Does he have","Did she go","Could she have","I go",
            "We have","It's important","My team is ready",
            "An unusual situation","The report is complete",
            "A university lecture","A European initiative",
            "The IM standard","IM","The document arrived",
            "There are many","He can do","An honest person",
            "Keep me posted on the outcome")
        var correct=0
        val misses=ArrayList<String>();val times=ArrayList<Long>()
        for((input,target) in wrong) {
            val start=SystemClock.elapsedRealtimeNanos()
            val fix=KeyPopGrammarRules.suggest(input,0)
            times.add(SystemClock.elapsedRealtimeNanos()-start)
            val fixed=if(fix==null)input else input.dropLast(fix.remove)+fix.replacement
            if(fixed.equals(target,ignoreCase=true))correct++
            else misses.add(input+">"+target)
        }
        record("GRAMMAR_RECALL",wrong.size,correct,correct,misses,times)
        var falseEdits=0
        for(input in valid) {
            val fix=KeyPopGrammarRules.suggest(input,0)
            if(fix!=null){falseEdits++;misses.add("FALSE_EDIT:"+input)}
        }
        record("GRAMMAR_FALSE_EDITS",valid.size,falseEdits,
            valid.size-falseEdits,misses,emptyList())
    }

    @Test fun criticalRegressionsRemainHardReleaseGates() {
        assertTrue(lexicon.isReady())
        assertEquals("I'm",lexicon.fastCorrection(0,"im","im"))
        assertNull(lexicon.fastCorrection(0,"IM","IM"))
        val continuation=next("Keep my colleagues posted ")
        assertTrue(continuation.contains("on"))
        assertTrue(continuation.contains("about"))
        assertNull(lexicon.suggestedCorrection(0,"their","I read their"))
    }
}
