package com.aetherkeys.keyboard

import java.util.Locale

/**
 * Tiny always-ready common-word repair layer.  Large dictionary/fuzzy searches
 * run asynchronously, but the suggestion strip and Space need useful candidates
 * even for two-letter deletions such as "te" -> "the".
 *
 * This is a generic one-edit mechanism, NOT an unconditional typo replacement.
 * Valid full words are protected in the caller, while ambiguous short tokens
 * only auto-correct with supportive surrounding context.
 */
object KeyPopShortWordRecovery {
    private val vocabulary=listOf(
        "the","and","you","that","have","for","not","with","this","but",
        "his","from","they","her","she","will","one","all","would","there",
        "their","what","about","which","when","make","can","like","time",
        "just","know","take","people","your","good","some","could","them",
        "see","other","than","then","now","look","only","come","over",
        "think","also","back","after","use","two","how","our","work","first",
        "well","way","even","new","want","because","any","these","give",
        "day","most","was","are","were","did","does","do","done","at","an",
        "as","be","by","he","we","me","my","is","it","of","or","on","to",
        "in","if","us","up","go","so","no","yes","hello","here","where",
        "who","why","please","thank","thanks","check","send","share",
        "need","file","report","word","keyboard","text","type","typed",
        "typing","message","correct","correction","more","much","should"
    )
    private val common=vocabulary.toSet()
    private val preferredBeforeThe=setOf(
        "see","read","check","review","open","send","share","with","for",
        "in","on","at","of","to","about","from","by","and","over","under",
        "during","after","before","please","is","was","are","were",
        "have","has","had","did","does","do","need","want","show",
        "read","view","find","follow","write","type","get","use"
    )
    private val previousWord=Regex("[\\p{L}]+$")
    data class Match(val word:String,val score:Int)

    private fun normalize(word:String)=word.trim().lowercase(Locale.ROOT)
    fun suggestions(raw:String,before:String="",limit:Int=5):List<String> {
        val input=normalize(raw)
        if(input.length !in 2..6 || !input.all{it in 'a'..'z'})return emptyList()
        val context=before.removeSuffix(raw).trimEnd()
        val prev=previousWord.find(context)?.value?.lowercase(Locale.ROOT).orEmpty()
        return vocabulary.asSequence().mapNotNull { candidate ->
            if(candidate==input || kotlin.math.abs(candidate.length-input.length)>1)
                return@mapNotNull null
            val distance=KeyPopFuzzyRanker.distanceAtMost2(input,candidate)
            if(distance!=1)return@mapNotNull null
            var score=500-vocabulary.indexOf(candidate)*2
            if(candidate.first()==input.first())score+=40
            if(candidate.length>input.length)score+=7
            if(candidate=="the" && prev in preferredBeforeThe)score+=160
            if(candidate=="does" && prev in setOf("he","she","it","who","what","why"))
                score+=110
            Match(candidate,score)
        }.sortedWith(compareByDescending<Match>{it.score}.thenBy{it.word})
            .take(limit.coerceIn(1,8)).map{it.word}.toList()
    }

    fun safeAutomatic(raw:String,before:String):String? {
        val input=normalize(raw)
        if(input.length !in 2..6 || input in common)return null
        val options=suggestions(raw,before,3)
        val top=options.firstOrNull()?:return null
        val previous=previousWord.find(before.removeSuffix(raw).trimEnd())
            ?.value?.lowercase(Locale.ROOT).orEmpty()
        // Short stems may be intentional (e.g. names or foreign words).
        // Only permit the very common missing-H form in strong prose contexts.
        if(input=="te")return top.takeIf{it=="the" && previous in preferredBeforeThe}
        // Other tiny inputs need at least two preceding words AND a clear
        // single-edit winner. The strip still offers alternatives earlier.
        if(input.length<4)return null
        if(KeyPopUnicodeWords.tokens(before.removeSuffix(raw)).size<2)return null
        return top.takeIf { options.getOrNull(1)==null &&
            it.first()==input.first() }
    }
}
