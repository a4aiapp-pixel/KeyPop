package com.aetherkeys.keyboard

import java.util.Locale

/**
 * QWERTY number-row accidental presses in an otherwise alphabetic word.
 * No global 3->e replacement: numeric text, model IDs, login tokens and URLs
 * must not be silently modified. Only a single interior digit can be offered
 * and the caller must validate the result against a real known-word lexicon.
 */
object KeyPopDigitSlipRules {
    private val adjacentLetter=mapOf(
        '1' to 'q','2' to 'w','3' to 'e','4' to 'r','5' to 't',
        '6' to 'y','7' to 'u','8' to 'i','9' to 'o','0' to 'p'
    )
    private val singleInteriorDigit=Regex("^[a-z]{1,24}[0-9][a-z]{1,24}$")

    fun repairCandidate(raw:String):String? {
        val input=raw.trim().lowercase(Locale.ROOT)
        if(input.length !in 4..25 || !singleInteriorDigit.matches(input))return null
        val index=input.indexOfFirst(Char::isDigit)
        val letter=adjacentLetter[input[index]] ?:return null
        return input.replaceRange(index,index+1,letter.toString())
    }
}
