package com.aetherkeys.keyboard

/**
 * Shared glide eligibility for every visual preset and alphabetic layout.
 * No theme identity appears here: changing a skin must not disable typing.
 * Sensitive, numeric, URL and email editors never enter the glide decoder.
 */
object KeyPopGlidePolicy {
    fun enabled(toggle:Boolean,language:Int,token:String,symbols:Boolean,
                numeric:Boolean,privateEditor:Boolean,inputType:Int):Boolean {
        if(!toggle || symbols || numeric || privateEditor || language !in 0..2)
            return false
        if(!KeyPopTypingRules.textAllowsCapitalization(inputType))return false
        return token.codePointCount(0,token.length)==1 &&
            token.codePoints().allMatch {Character.isLetter(it)}
    }
}

/** Lightweight Unicode-aware fallback for Urdu/Sindhi and custom alphabets.
 * The normal English decoder uses its pre-indexed SCOWL vocabulary, whereas
 * this decoder intentionally scans only a bounded regional word list.
 */
object KeyPopGlideScoring {
    private fun compact(raw:String):String=buildString {
        for(ch in KeyPopUnicodeWords.normalize(raw)) {
            if(!ch.isLetter())continue
            if(isEmpty() || last()!=ch)append(ch)
        }
    }

    fun trace(path:List<String>):String {
        if(path.size<3 || path.size>120)return ""
        return compact(path.filter { item ->
            item.codePointCount(0,item.length)==1 &&
                item.codePoints().allMatch {Character.isLetter(it)}
        }.joinToString(""))
    }

    private fun editDistance(a:String,b:String):Int {
        var previous=IntArray(b.length+1){it}
        var current=IntArray(b.length+1)
        for(i in a.indices) {
            current[0]=i+1
            for(j in b.indices) {
                current[j+1]=minOf(previous[j+1]+1,current[j]+1,
                    previous[j]+if(a[i]==b[j])0 else 1)
            }
            val swap=previous;previous=current;current=swap
        }
        return previous[b.length]
    }

    /** Reject paths that cannot plausibly represent the target word. */
    fun rank(path:List<String>,dictionary:List<String>,limit:Int=3):List<String> {
        val entered=trace(path)
        if(entered.length !in 3..25)return emptyList()
        return dictionary.asSequence().distinct().take(3500)
            .mapNotNull { word ->
                val normal=compact(word)
                if(normal.length !in 3..26 || normal.first()!=entered.first() ||
                    normal.last()!=entered.last() ||
                    kotlin.math.abs(normal.length-entered.length)>2)
                    return@mapNotNull null
                val cost=editDistance(entered,normal)
                val allowance=if(entered.length>=7)2 else 1
                if(cost>allowance)return@mapNotNull null
                // Exact paths win; extra/missing key samples are tolerated.
                val score=300-cost*90-
                    kotlin.math.abs(normal.length-entered.length)*10
                word to score
            }.sortedWith(compareByDescending<Pair<String,Int>>{it.second}
                .thenBy{it.first.length}).take(limit.coerceIn(1,8))
            .map{it.first}.toList()
    }
}
