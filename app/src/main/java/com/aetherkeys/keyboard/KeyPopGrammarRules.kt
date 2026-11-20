package com.aetherkeys.keyboard

/**
 * Explicit, deterministic offline corrections. This is a deliberately narrow
 * rule set; it never claims to be a comprehensive grammar model.
 */
data class KeyPopGrammarFix(val label:String,val remove:Int,
                            val replacement:String,val automatic:Boolean=true)

object KeyPopGrammarRules {
    private val rules=listOf(
        Regex("(?i)\\bi\\s+is$") to "I am",
        Regex("(?i)\\bi\\s+are$") to "I am",
        Regex("(?i)\\bhe\\s+have$") to "he has",
        Regex("(?i)\\bshe\\s+have$") to "she has",
        Regex("(?i)\\bit\\s+have$") to "it has",
        Regex("(?i)\\bthey\\s+is$") to "they are",
        Regex("(?i)\\bwe\\s+is$") to "we are",
        Regex("(?i)\\byou\\s+is$") to "you are",
        Regex("(?i)\\bi\\s+has$") to "I have",
        Regex("(?i)\\bwe\\s+has$") to "we have",
        Regex("(?i)\\bthey\\s+has$") to "they have",
        Regex("(?i)\\byou\\s+has$") to "you have",
        Regex("(?i)\\bhe\\s+don't$") to "he doesn't",
        Regex("(?i)\\bshe\\s+don't$") to "she doesn't",
        Regex("(?i)\\bit\\s+don't$") to "it doesn't",
        Regex("(?i)\\byou\\s+was$") to "you were",
        Regex("(?i)\\bwe\\s+was$") to "we were",
        Regex("(?i)\\bthey\\s+was$") to "they were",
        Regex("(?i)\\bthere\\s+is\\s+many$") to "there are many",
        Regex("(?i)\\bhe\\s+are$") to "he is",
        Regex("(?i)\\bshe\\s+are$") to "she is",
        Regex("(?i)\\bit\\s+are$") to "it is",
        Regex("(?i)\\bhe\\s+were$") to "he was",
        Regex("(?i)\\bshe\\s+were$") to "she was",
        Regex("(?i)\\bit\\s+were$") to "it was",
        Regex("(?i)\\bi\\s+have\\s+went$") to "I have gone",
        Regex("(?i)\\bhe\\s+have\\s+went$") to "he has gone",
        Regex("(?i)\\bi\\s+am\\s+agree$") to "I agree",
        Regex("(?i)\\bdid\\s+not\\s+went$") to "did not go",
        Regex("(?i)\\bi\\s+does$") to "I do",
        Regex("(?i)\\bi\\s+doesn't$") to "I don't",
        Regex("(?i)\\bi\\s+goes$") to "I go",
        Regex("(?i)\\bhe\\s+do$") to "he does",
        Regex("(?i)\\bshe\\s+do$") to "she does",
        Regex("(?i)\\bit\\s+do$") to "it does",
        Regex("(?i)\\bhe\\s+go$") to "he goes",
        Regex("(?i)\\bshe\\s+go$") to "she goes",
        Regex("(?i)\\bit\\s+go$") to "it goes",
        Regex("(?i)\\bwe\\s+goes$") to "we go",
        Regex("(?i)\\bthey\\s+goes$") to "they go",
        Regex("(?i)\\byou\\s+goes$") to "you go",
        Regex("(?i)\\bwe\\s+doesn't$") to "we don't",
        Regex("(?i)\\bthey\\s+doesn't$") to "they don't",
        Regex("(?i)\\byou\\s+doesn't$") to "you don't",
        Regex("(?i)\\bdid\\s+went$") to "did go",
        Regex("(?i)\\bdid\\s+ate$") to "did eat",
        Regex("(?i)\\bdid\\s+saw$") to "did see",
        Regex("(?i)\\bdoesn't\\s+has$") to "doesn't have",
        Regex("(?i)\\bhas\\s+went$") to "has gone",
        Regex("(?i)\\bhave\\s+went$") to "have gone",
        Regex("(?i)\\bdoes\\s+not\\s+has$") to "does not have",
        Regex("(?i)\\bdont$") to "don't",
        Regex("(?i)\\bdoesnt$") to "doesn't",
        Regex("(?i)\\bcant$") to "can't",
        Regex("(?i)\\bteh$") to "the",
        Regex("(?i)\\badn$") to "and",
        Regex("(?i)\\brecieve$") to "receive",
        Regex("(?i)\\bdefinately$") to "definitely",
        Regex("(?i)\\bseperate$") to "separate"
    )
    /** A fix only applies at the cursor and only after checking the text again. */
    fun suggest(before:String,language:Int):KeyPopGrammarFix? {
        if(language!=0 || before.isBlank())return null
        val text=before.takeLast(150)
        // Single-token contractions are genuine grammar improvements too:
        // "im" and "Im" -> "I'm", but intentional "IM" stays untouched.
        val lastWord=Regex("[A-Za-z]+$").find(text)?.value.orEmpty()
        KeyPopContractionRules.correction(lastWord)?.let { replacement ->
            return KeyPopGrammarFix("Fix: "+replacement,
                lastWord.length,replacement,true)
        }
        for((pattern,replacement) in rules) {
            val match=pattern.find(text) ?:continue
            if(match.range.last != text.lastIndex)continue
            // Questions and auxiliaries license the bare verb: "Does he have"
            // and "Did she go" must never become "Does he has/goes".
            val prefix=text.substring(0,match.range.first).trimEnd()
            val preceding=Regex("[A-Za-z']+$").find(prefix)
                ?.value?.lowercase().orEmpty()
            val matched=match.value.lowercase()
            val bareVerbCandidate=Regex("^(he|she|it)\\s+(have|do|go)$")
                .matches(matched)
            if(bareVerbCandidate && preceding in setOf("do","does","did",
                    "can","could","shall","should","will","would","may",
                    "might","must","let"))continue
            val adjusted=if(match.value.firstOrNull()?.isUpperCase()==true)
                replacement.replaceFirstChar{it.uppercase()} else replacement
            return KeyPopGrammarFix("Fix: "+adjusted,match.value.length,adjusted)
        }
        // Articles depend on pronunciation and exceptions. Suggest only;
        // never silently insert a/an on Space without a pronunciation model.
        val article=Regex("(?i)\\b(a|an)\\s+([a-z]{2,20})$").find(text)
        if(article!=null && article.range.last==text.lastIndex) {
            val noun=article.groupValues[2]
            val normal=noun.lowercase()
            // Common phonetic exceptions: vowel letter != vowel sound.
            val silentH=setOf("hour","honest","honor","honour","heir")
            val consonantSound=setOf("university","uniform","unique","user",
                "unit","union","unicorn","european","euro","one","once")
            val expected=when {
                normal in silentH -> "an"
                normal in consonantSound -> "a"
                normal.first() in "aeiou" -> "an"
                else -> "a"
            }
            if(article.groupValues[1].lowercase()!=expected) {
                val replacement=expected+" "+noun
                return KeyPopGrammarFix("Suggestion: "+replacement,
                    article.value.length,replacement,false)
            }
        }
        val pair=Regex("(?i)\\b([a-z]{2,})\\s+\\1$").find(text)
        if(pair!=null && pair.range.last==text.lastIndex){
            val single=pair.groupValues[1]
            return KeyPopGrammarFix("Remove repeated "+single,
                pair.value.length,single,false)
        }
        return null
    }
}
