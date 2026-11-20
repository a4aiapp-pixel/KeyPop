package com.aetherkeys.keyboard

/**
 * Reusable, on-device English linguistic frames for next-word suggestions.
 * These are grammatical compatibility priors, not literal sentences copied
 * from a keyboard vendor.  No user text is uploaded or retained here.
 */
object KeyPopLinguisticContinuations {
    data class Candidate(val word:String,val score:Int)

    private val communicate=setOf("keep","keeps","kept","keeping")
    private val recipients=setOf("me","us","you","him","her","them",
        "everyone","somebody","someone","everybody")
    private val updateVerbs=setOf("update","updates","updated","updating")
    private val informVerbs=setOf("inform","informs","informed","informing")
    private val subjectSingular=setOf("he","she","it")
    private val subjectPlural=setOf("we","you","they")
    private fun fits(words:List<String>,sequence:List<String>):Boolean =
        words.size>=sequence.size && words.takeLast(sequence.size)==sequence

    /**
     * Consider a clause boundary before interpreting a frame. Do not suggest
     * the same continuation after a sentence-ending period or question.
     */
    fun ranked(language:Int,before:String,fragment:String=""):List<Candidate> {
        if(language!=0)return emptyList()
        val current=KeyPopUnicodeWords.normalize(fragment)
        val preceding=if(fragment.isNotEmpty() &&
            before.endsWith(fragment,ignoreCase=true))
            before.dropLast(fragment.length) else before
        val tail=preceding.takeLast(200)
        val boundary=tail.indexOfLast { it in ".!?؟۔\n" }
        val clause=tail.substring(boundary+1)
        val h=KeyPopUnicodeWords.tokens(clause,40).takeLast(7)
        if(h.isEmpty())return emptyList()
        val last=h.last()
        val beforeLast=h.getOrNull(h.lastIndex-1)
        val thirdLast=h.getOrNull(h.lastIndex-2)
        val fourthLast=h.getOrNull(h.lastIndex-3)

        val proposals=when {
            // "Keep me posted" and "keep the team posted" use the same
            // prepositional frame, regardless of the exact subject or style.
            last=="posted" &&
                (thirdLast in communicate && beforeLast!=null ||
                    fourthLast in communicate &&
                    thirdLast in setOf("my","our","the","your") ||
                    h.takeLast(4).any{it in communicate} &&
                    h.takeLast(4).any{it in recipients}) ->
                listOf("on" to 525,"about" to 495,"regarding" to 480)

            last in setOf("updated","informed") &&
                (thirdLast in communicate || h.takeLast(4).any {
                    it in communicate || it in informVerbs
                }) ->
                listOf("on" to 470,"about" to 465,"regarding" to 450)

            last in recipients && beforeLast in updateVerbs ->
                listOf("on" to 500,"about" to 480,"regarding" to 465)

            last in recipients && beforeLast in informVerbs ->
                listOf("about" to 500,"of" to 480,"regarding" to 440)

            fits(h,listOf("looking","forward","to")) ||
                fits(h,listOf("look","forward","to")) ->
                listOf("hearing" to 505,"seeing" to 465,
                    "working" to 445,"meeting" to 435)

            fits(h,listOf("thank","you","for")) ->
                listOf("your" to 485,"the" to 455,
                    "helping" to 440,"this" to 425)

            fits(h,listOf("please","let","me")) ->
                listOf("know" to 500,"check" to 420,"see" to 410)

            last=="i" ->
                listOf("am" to 470,"have" to 450,"will" to 425,"can" to 420)

            last in subjectSingular ->
                listOf("is" to 475,"has" to 445,"will" to 430)

            last in subjectPlural ->
                listOf("are" to 465,"have" to 450,"will" to 430)

            else -> emptyList()
        }
        return proposals.asSequence()
            .filter{current.isEmpty() || it.first.startsWith(current)}
            .map{Candidate(it.first,it.second)}
            .toList()
    }
}
