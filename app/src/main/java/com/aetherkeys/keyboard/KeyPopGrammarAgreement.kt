package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Conservative English agreement prior for ranking only.
 * No candidate is automatically inserted or substituted by this class.
 * Never treat a lexical heuristic as a complete grammar checker.
 */
object KeyPopGrammarAgreement {
    private val terms=Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")
    private val singular=setOf("he","she","it")
    private val plural=setOf("we","you","they")
    private val auxiliaries=setOf("am","is","are","was","were","have","has","had",
        "can","could","will","would","should","may","might","must","do","does","did")
    private val bareVerbs=setOf("do","have","be","go","get","make","take","send","share",
        "check","write","read","see","know","help","find","review","work","start",
        "finish","continue","proceed","prepare","change","choose","show","open",
        "receive","download","update","tell","think","look","try","want","need")

    private fun history(before:String,typed:String):List<String> {
        val prefix=if(typed.isNotEmpty()&&before.endsWith(typed,ignoreCase=true))
            before.dropLast(typed.length) else before
        return terms.findAll(prefix.takeLast(140))
            .map{it.value.lowercase(Locale.ROOT)}.toList().takeLast(3)
    }

    /** Candidate vocabulary for common agreement and auxiliary contexts. */
    fun seeds(before:String,typed:String):List<String> {
        val last=history(before,typed).lastOrNull()?:return emptyList()
        val choices=when(last) {
            "i" -> listOf("am","have","will","can","would","was","do")
            "he","she","it" -> listOf("is","has","was","will","can","does")
            "you","we","they" -> listOf("are","have","were","will","can","do")
            "will","can","could","would","should","must","did" -> 
                listOf("be","have","do","get","go","make","send","check","help","see")
            "is","are","am" -> listOf("not","a","the","going","working","ready")
            "have","has" -> listOf("been","done","received","completed","sent","already")
            "please" -> listOf("check","send","share","confirm","review","let")
            "thank" -> listOf("you")
            else -> emptyList()
        }
        return choices.filter{typed.isBlank() || it.startsWith(typed,ignoreCase=true)}
    }

    /** Negative scores suppress demonstrably wrong subject-verb agreement. */
    fun score(before:String,typed:String,candidate:String):Int {
        val h=history(before,typed)
        val last=h.lastOrNull()?:return 0
        val word=candidate.lowercase(Locale.ROOT)
        if(word.isEmpty() || word.contains(' '))return 0
        if(last=="i" && h.getOrNull(h.lastIndex-1)=="if" && word=="were")
            return 90
        if(last=="i") {
            if(word in setOf("is","are","has","were","does"))return -700
            if(word in setOf("am","have","will","can","would","was","do"))return 130
        }
        if(last in singular) {
            if(word in setOf("am","are","have","were","do"))return -700
            if(word in setOf("is","has","was","does","will","can"))return 130
        }
        if(last in plural) {
            if(word in setOf("am","is","has","was","does"))return -700
            if(word in setOf("are","have","were","do","will","can"))return 130
        }
        if(last in setOf("will","can","could","would","should","must","did")) {
            if(word in setOf("is","are","am","has","does"))return -550
            if(word in bareVerbs)return 85
            if(word.endsWith("ing") && typed.isBlank())return -100
        }
        if(last=="please" && word in setOf("check","send","share","confirm",
                "review","let","provide","help"))return 105
        if(last=="thank" && word=="you")return 210
        if(last=="have" || last=="has") {
            if(word in setOf("been","done","received","completed","sent"))return 75
        }
        // Do not impose grammar on articles, proper nouns or inflected
        // forms that need pronunciation or deeper parsing.
        return 0
    }
}
