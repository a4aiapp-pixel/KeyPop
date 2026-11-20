package com.aetherkeys.keyboard

/** Rule-based, private text transforms: intentionally not marketed as neural AI. */
object KeyPopWritingTools {
    /**
     * Optional one-tap offline proofread across an explicitly selected draft.
     * Conservative high-confidence grammar and typo rules, not a neural model.
     * Never records, uploads, or learns the source. Caller previews the result
     * and must explicitly press Replace.
     */
    fun proofread(text:String,language:Int):String {
        if(language!=0 || text.isBlank() || text.length>5000)return text
        val token=Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*")
        val output=StringBuilder()
        var previousEnd=0
        for(match in token.findAll(text)) {
            output.append(text,previousEnd,match.range.last+1)
            val before=output.toString()
            val target=match.value
            val context=before.dropLast(target.length)
            val fixed=KeyPopTypingIntelligence.correction(0,target)
                ?:KeyPopShortWordRecovery.safeAutomatic(target,before)
            if(fixed!=null && fixed!=target) {
                output.setLength(output.length-target.length)
                output.append(KeyPopTypingRules.preservesCase(target,fixed))
            }
            val grammar=KeyPopGrammarRules.suggest(output.toString(),0)
            if(grammar!=null && grammar.automatic && grammar.remove<=output.length) {
                output.setLength(output.length-grammar.remove)
                output.append(grammar.replacement)
            }
            previousEnd=match.range.last+1
        }
        output.append(text,previousEnd,text.length)
        return output.toString()
    }
    private val whitespace=Regex("[\\t ]+")
    private val badSpace=Regex("\\s+([,;:.!?])")
    fun rewrite(text:String,mode:String,language:Int):String {
        if(text.isBlank()||text.length>5000)return text
        var value=text.trim().replace(whitespace," ").replace(badSpace,"$1")
        if(language==0){
            value=when(mode){
                "Proofread" -> proofread(value,language)
                "Formal" -> value.replace(Regex("\\bcan't\\b",RegexOption.IGNORE_CASE),"cannot")
                    .replace(Regex("\\bwon't\\b",RegexOption.IGNORE_CASE),"will not")
                    .replace(Regex("\\bgonna\\b",RegexOption.IGNORE_CASE),"going to")
                    .replace(Regex("\\bwanna\\b",RegexOption.IGNORE_CASE),"want to")
                    .let{ if(it.startsWith("hi ",ignoreCase=true))"Hello "+it.drop(3) else it }
                "Polite"->if(value.startsWith("please",ignoreCase=true))value
                    else "Please "+value.replaceFirstChar{it.lowercase()}
                "Concise"->value.replace(Regex("\\b(really|very|actually|basically|just)\\s+",RegexOption.IGNORE_CASE),"")
                    .replace(Regex("\\s+")," ")
                else->value
            }
            if(value.isNotEmpty())value=value.replaceFirstChar{it.uppercase()}
            if(mode=="Punctuate" && value.lastOrNull()?.isLetter()==true)value+="."
        } else {
            if(mode=="Polite" && !value.startsWith(if(language==1)"براہ کرم" else "مهرباني ڪري"))
                value=(if(language==1)"براہ کرم " else "مهرباني ڪري ")+value
            if(mode=="Punctuate" && value.lastOrNull()?.isLetter()==true)value+="۔"
        }
        return value
    }
}
