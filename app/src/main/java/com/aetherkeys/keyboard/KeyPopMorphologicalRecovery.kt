package com.aetherkeys.keyboard

import java.util.Locale

/**
 * General orthographic repair of unfamiliar stems. Rather than including a
 * large table of benchmark-specific misspellings, generates likely single
 * vowel omissions and extra repeated letters, then validates each against
 * the existing, licensed offline SCOWL dictionary.
 *
 * Suggestions only. NEVER causes a silent Space replacement or learns words.
 */
object KeyPopMorphologicalRecovery {
    fun suggestions(raw:String,known:(String)->Boolean,limit:Int=5):List<String> {
        val token=raw.lowercase(Locale.ROOT)
        if(token.length !in 5..22 || token.any{it !in 'a'..'z'} ||
            known(token))return emptyList()
        val possible=LinkedHashSet<String>()
        // An accidental doubled key is a common valid-looking failure.
        for(i in 1 until token.length) {
            if(token[i]==token[i-1])
                possible.add(token.removeRange(i,i+1))
        }
        // One missing vowel can occur in any stem, including between two
        // consonants; sample both interior and final-stem positions.
        for(i in 1 until token.length) {
            if(token[i-1] in "aeiou" && token[i] in "aeiou")continue
            for(letter in "aeiou")possible.add(token.substring(0,i)+
                letter+token.substring(i))
        }
        // Misordered letters (Damerau adjacent swaps).
        for(i in 1 until token.length) {
            if(token[i]==token[i-1])continue
            val chars=token.toCharArray()
            val tmp=chars[i];chars[i]=chars[i-1];chars[i-1]=tmp
            possible.add(String(chars))
        }
        return possible.asSequence().filter(known).take(limit.coerceIn(1,8)).toList()
    }
}
