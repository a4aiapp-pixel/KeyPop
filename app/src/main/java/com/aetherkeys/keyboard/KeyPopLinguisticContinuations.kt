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
    private val pluralNominals=setOf("items","files","documents","reports",
        "requests","cases","records","results","proposals","updates",
        "applications","systems","features","options","details","questions")
    private val communicationVerbs=setOf("send","share","provide","submit",
        "attach","give","forward","show","offer","include","deliver")
    private val obligation=setOf("must","should","shall","might","may")
    private val modal=setOf("will","would","can","could","shall","should",
        "may","might","must")
    private val messageObjects=setOf("report","document","file","case",
        "request","application","proposal","information","result","update")
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

            last=="informed" &&
                (thirdLast in communicate || h.takeLast(4).any {
                    it in communicate || it in informVerbs
                }) ->
                listOf("about" to 525,"of" to 490,"regarding" to 480,
                    "on" to 460)

            last=="updated" &&
                (thirdLast in communicate || h.takeLast(4).any {
                    it in communicate || it in updateVerbs
                }) ->
                listOf("on" to 500,"about" to 475,"regarding" to 462)

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

            // Reusable grammatical dependency frames. The candidate sets
            // depend on word classes and auxiliary position, not one exact
            // memorized full sentence.
            last=="could" && beforeLast in setOf("you","we","i","they") ->
                listOf("please" to 608,"be" to 455,"help" to 445,
                    "send" to 437,"provide" to 430)

            last=="please" && beforeLast in setOf("you","we","they",
                    "he","she") ->
                listOf("check" to 600,"send" to 580,
                    "confirm" to 570,"help" to 555,"review" to 545)

            last in setOf("remains","remain") ->
                listOf("open" to 476,"unclear" to 470,"ongoing" to 465,
                    "under" to 460,"to" to 440)

            last in setOf("was","were") &&
                beforeLast in messageObjects ->
                listOf("submitted" to 500,"prepared" to 483,
                    "received" to 471,"approved" to 465,"under" to 452)

            last in setOf("is","are","was","were") ->
                listOf("available" to 463,"being" to 458,
                    "ready" to 450,"required" to 445,"the" to 422)

            last=="that" ->
                listOf("the" to 480,"it" to 461,"this" to 453,
                    "we" to 442,"you" to 433)

            last=="whether" ->
                listOf("the" to 485,"you" to 471,"it" to 457,
                    "this" to 451,"we" to 442)

            last in pluralNominals ->
                listOf("are" to 485,"have" to 465,
                    "were" to 450,"will" to 438)

            last in communicationVerbs ->
                listOf("the" to 485,"a" to 460,"more" to 450,
                    "your" to 441,"an" to 430)

            last in setOf("proceed","continue","follow") ->
                listOf("with" to 485,"to" to 470,"now" to 460,
                    "as" to 435)

            last in obligation ->
                listOf("be" to 485,"have" to 458,"ensure" to 448,
                    "consider" to 438,"not" to 432)

            last=="ensure" ->
                listOf("that" to 500,"the" to 477,"all" to 470,
                    "compliance" to 455)

            last in setOf("review","assess","check","confirm") ->
                listOf("the" to 480,"this" to 461,"whether" to 454,
                    "all" to 450)

            last=="information" ->
                listOf("please" to 469,"about" to 456,
                    "contact" to 453,"regarding" to 442)

            last=="of" ->
                listOf("the" to 487,"this" to 467,"our" to 459,
                    "all" to 445)

            last=="to" && beforeLast in setOf("need","needs","want",
                    "wants","have","has","had","used","required") ->
                listOf("check" to 470,"review" to 466,"update" to 460,
                    "be" to 453,"ensure" to 446)

            last in modal ->
                listOf("be" to 465,"have" to 458,"do" to 454,
                    "provide" to 448,"help" to 443)

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
