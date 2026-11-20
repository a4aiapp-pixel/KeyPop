package com.aetherkeys.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyPopRC51ProofreadTest {
    @Test fun wholeDraftFixesMultipleAgreementAndSpellingErrors() {
        assertEquals("I am reviewing the report.",
            KeyPopWritingTools.rewrite("I are reviewing the report.","Proofread",0))
        assertEquals("Please check the report.",
            KeyPopWritingTools.rewrite("Please check te report.","Proofread",0))
        assertEquals("He goes to work.",
            KeyPopWritingTools.rewrite("He go to work.","Proofread",0))
        assertEquals("They are working.",
            KeyPopWritingTools.rewrite("They is working.","Proofread",0))
    }
    @Test fun alreadyCorrectQuestionsAndNativeScriptsArePreserved() {
        assertEquals("Does he have a document?",
            KeyPopWritingTools.rewrite("Does he have a document?","Proofread",0))
        assertEquals("Can she go tomorrow?",
            KeyPopWritingTools.rewrite("Can she go tomorrow?","Proofread",0))
        assertEquals("سنڌي متن",
            KeyPopWritingTools.rewrite("سنڌي متن","Proofread",2))
    }
    @Test fun longDraftsAreNotSilentlyProcessed() {
        val long="word ".repeat(1100)
        assertEquals(long,KeyPopWritingTools.rewrite(long,"Proofread",0))
    }
}
