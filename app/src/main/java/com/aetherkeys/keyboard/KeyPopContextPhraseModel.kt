package com.aetherkeys.keyboard

/**
 * Compact on-device n-gram backoff for common contexts. Static examples are
 * only a cold-start prior: private phrases learned by the user are combined
 * by the unified ranker and are never uploaded or bundled back into the app.
 */
object KeyPopContextPhraseModel {
    private val examples=mapOf(
        0 to listOf(
            "please check the latest version","please confirm the details",
            // Multiword continuation examples cover ordinary status updates.
            // Phrase indexing below learns overlapping 1-3 word contexts,
            // so e.g. "keep me posted" works anywhere in the sentence.
            "keep me posted on the progress","please keep me posted on the outcome",
            "keep me posted on any changes","can you keep me posted on this",
            "kindly keep me posted about the situation",
            "keep me posted regarding the status",
            "please keep me posted regarding the progress",
            "keep me posted regarding any further updates",
            "i would appreciate it if you keep me posted regarding the matter",
            "keep me posted about the results",
            "please keep me posted about any developments",
            "keep us posted on the results","please keep us posted on the updates",
            "keep them posted on the changes",
            "keep me updated on the progress","please keep me updated on the status",
            "keep us updated on the next steps","let me know about the progress",
            "let me know when it is ready","keep me informed about any developments",
            "keep me in the loop on this","please update me on the progress",
            "keep an eye on the situation","looking forward to hearing from you",
            "please follow up on the matter","i will keep you posted on the progress",
            "please share the final report","please review the document",
            "please find the attached document","please provide more information",
            "please let me know","please let us know",
            "thank you for your help","thank you very much",
            "thanks for the update","thanks for your support",
            "i am working on this","i am going to check",
            "i am writing to confirm","i am happy to help",
            "i will check and confirm","i will send the report",
            "i will share the details","i will get back to you",
            "we are looking into this","we are working on it",
            "we have received your request","we have reviewed the report",
            "we will share an update","we need to confirm",
            "could you please check","could you please share",
            "can you help me","can you please confirm",
            "would you like to proceed","would you please review",
            "the attached document contains","the report is ready",
            "the latest version is available","the issue has been resolved",
            "the results are available","the application is under review",
            "this is a good idea","this is the latest update",
            "the next step is to","the final version is ready",
            "it is important to note","it is working properly",
            "there are several options","there is an issue",
            "the keyboard should suggest","the prediction should improve",
            "the system is running","the project is complete",
            "how are you doing","how can i help",
            "how does this work","how much does it cost",
            "what do you mean","what is the issue",
            "where can i find","when will it be ready",
            "i need to check","i need to improve",
            "i want to know","i want to write",
            "there is no need to","this should work correctly",
            "the corrections are accurate","it should not change",
            "it is not working","the document has been received",
            "the report will be submitted","the budget has been approved",
            "the payment is pending","the details have been reviewed",
            "i have reviewed the document","we are pleased to inform",
            "please proceed with the changes","please continue with the project",
            "we appreciate your cooperation","looking forward to your reply",
            "please let me know if","please contact us for details",
            "kind regards and best wishes","best regards and thanks",
            "a better way to do","this may be considered further",
            "i hope you are doing well","i hope this helps","i think that we should",
            "i think this is important","i would like to know","i would like to request",
            "i would like to confirm","i would appreciate your help",
            "i am looking forward to hearing from you",
            "i am not sure about this","i am sorry for the delay",
            "i have already sent it","i have not received it",
            "i have been working on this","i have completed the task",
            "i can check it now","i can send it tomorrow",
            "i can help you with that","i cannot access the file",
            "i need more time to review","i need your feedback",
            "i need to send an email","i need to make a decision",
            "i want to make sure","i want you to know",
            "i will let you know","i will get it done",
            "i will be there soon","i will send you the details",
            "i will follow up tomorrow","i will take a look",
            "we have been working on this","we have not received the documents",
            "we can discuss this later","we can make it better",
            "we need to make sure","we need to update the application",
            "we should consider the options","we should focus on quality",
            "we would like to inform you","we would like to request approval",
            "you are absolutely right","you are very welcome",
            "you have made a good point","you have to check the details",
            "you can find it here","you can do this later",
            "you should be able to see it","you should not have to restart",
            "he is working on the project","she is working on the report",
            "it is going to rain","it is a good idea",
            "it is very important","it is not available yet",
            "it has been completed","it has been approved",
            "they are working on it","they have already received it",
            "they will be here tomorrow","they are ready to proceed",
            "there is a problem with the app","there is a better option",
            "there are many reasons for this","there are no changes yet",
            "this is what i mean","this is how it works",
            "this is not what i expected","this is exactly what i wanted",
            "that is a very good question","that is why it matters",
            "that would be very helpful","that should work fine",
            "the most important thing is","the best way to do this",
            "the app should open the settings","the keyboard should predict the next word",
            "the keyboard needs better suggestions","the keyboard is working correctly",
            "the issue is still there","the issue has not been resolved",
            "the system should work offline","the results will be available tomorrow",
            "the file has been uploaded","the documents have been submitted",
            "the report is being prepared","the meeting has been scheduled",
            "the request has been approved","the application will be reviewed",
            "the final version will be available","the updated version is ready",
            "please send me the details","please let me know when",
            "please check and confirm","please review and advise",
            "please find the details below","please share your feedback",
            "please provide the required information","please do not delete the file",
            "please try again later","please contact me if needed",
            "can you tell me more","can you send me the file",
            "can you please explain","can you please check now",
            "could you send it again","could you please help me",
            "could you please confirm receipt",
            "do you want to continue","do you have any questions",
            "do you know what happened","did you receive my message",
            "does this make sense","does it work on android",
            "how can we improve this","how do i change the settings",
            "how do i fix this","how long will it take",
            "what are the next steps","what do i need to do",
            "what happened to the settings","what is the best option",
            "when can we proceed","when is the next meeting",
            "where did you find it","where is the file saved",
            "thank you for the information","thank you for your patience",
            "thank you for your response","thanks for letting me know",
            "thanks for checking this","looking forward to hearing from you",
            "best regards and thank you","kind regards and thank you"

        ),
        1 to listOf(
            "براہ کرم معلومات فراہم کریں","براہ کرم رپورٹ ارسال کریں",
            "براہ کرم فائل ملاحظہ کریں","آپ کا بہت شکریہ",
            "ہم نے درخواست وصول کی","ہم نے رپورٹ تیار کی",
            "یہ مسئلہ حل ہو گیا","یہ معلومات دستیاب ہیں",
            "براہ کرم تصدیق کریں","آپ کی درخواست منظور ہوئی",
            "اس معاملے پر غور کیا جائے","یہ کام مکمل ہو گیا",
            "ہم آپ کو اطلاع دیں گے","رپورٹ کے مطابق فیصلہ کیا گیا",
            "درخواست مزید کارروائی کے لیے بھیجی گئی",
            "میں آپ سے درخواست کرتا ہوں","میں آپ کو بتانا چاہتا ہوں",
            "میں نے رپورٹ تیار کی ہے","میں کل آپ کو بتاؤں گا",
            "آپ کیسے ہیں","آپ کا شکریہ","آپ مجھے بتائیں",
            "آپ کی مدد کا شکریہ","یہ ایک اچھا فیصلہ ہے",
            "یہ کام ابھی جاری ہے","یہ بہت ضروری ہے",
            "یہ درخواست منظور ہو گئی","ہم اس پر کام کر رہے ہیں",
            "ہم آپ کی مدد کریں گے","ہم جلد جواب دیں گے",
            "کیا آپ مدد کر سکتے ہیں","کیا آپ بتا سکتے ہیں",
            "براہ کرم جلد جواب دیں","براہ کرم یہ فائل دیکھیں",
            "براہ کرم مزید معلومات دیں","کل تک کام مکمل ہو جائے گا"

        ),
        2 to listOf(
            "مهرباني ڪري معلومات ڏيو","مهرباني ڪري جواب ڏيو",
            "مهرباني ڪري رپورٽ موڪليو","توهان جي مهرباني",
            "اسان کي خبر ڏيو","اسان جي آفيس ۾",
            "اڄ جو ڪم مڪمل ٿيو","هن مسئلي جو حل",
            "اهي معلومات موجود آهن","مان توهان جي مدد ڪندس",
            "مهرباني ڪري تصديق ڪريو","سڀاڻي صبح جو وقت",
            "هن ڪم بابت ڄاڻ ڏيو",
            "مان توهان جي مدد ڪري سگهان ٿو","مان اڄ ڪم ڪري رهيو آهيان",
            "توهان ڪيئن آهيو","توهان جي مدد لاءِ مهرباني",
            "اسان کي وڌيڪ معلومات گهرجي","اسان هن تي ڪم ڪري رهيا آهيون",
            "اسان توهان کي ٻڌائينداسين","هي ڪم اڃا جاري آهي",
            "هي هڪ سٺو خيال آهي","هي رپورٽ تيار آهي",
            "مهرباني ڪري جلد جواب ڏيو","مهرباني ڪري وڌيڪ ڄاڻ ڏيو",
            "ڇا توهان منهنجي مدد ڪري سگهو ٿا","سڀاڻي تائين ڪم مڪمل ٿيندو"

        )
    )
    data class Candidate(val word:String,val score:Int,val depth:Int)

    private val bank:Map<Int,Map<String,List<Pair<String,Int>>>> by lazy {
        examples.mapValues { (_,sentences) ->
            val transitions=HashMap<String,MutableMap<String,Int>>()
            for(sentence in sentences) {
                val words=KeyPopUnicodeWords.tokens(sentence,60)
                for(i in 1 until words.size) {
                    for(depth in 1..minOf(i,3)) {
                        val context=words.subList(i-depth,i).joinToString("|")
                        val frequencies=transitions.getOrPut(context){LinkedHashMap()}
                        frequencies[words[i]]=(frequencies[words[i]]?:0)+1
                    }
                }
            }
            transitions.mapValues { (_,frequencies) ->
                frequencies.entries.sortedWith(
                    compareByDescending<Map.Entry<String,Int>>{it.value}.thenBy{it.key}
                ).map {it.key to it.value}
            }
        }
    }

    /**
     * Cold-start context likelihood, using three-word to one-word backoff.
     * This is a tiny bundled phrase prior, not a neural language model.
     * It contains no user text and never accesses a network.
     */
    fun ranked(language:Int,before:String,fragment:String="",limit:Int=12):List<Candidate> {
        val stem=KeyPopUnicodeWords.normalize(fragment)
        val previous=if(stem.isNotEmpty() && before.endsWith(fragment,ignoreCase=true))
            before.dropLast(fragment.length) else before
        val recent=previous.takeLast(200)
        val lastBoundary=recent.indexOfLast { it in ".!?؟۔\\n" }
        // A sentence boundary invalidates its old grammatical frame.
        // A new message starts with generic or personally learned priors,
        // not a stale continuation such as "posted on" after a full stop.
        val clause=recent.substring(lastBoundary+1)
        val history=KeyPopUnicodeWords.tokens(clause,32).takeLast(3)
        if(history.isEmpty())return emptyList()
        val transitions=bank[language].orEmpty()
        val output=LinkedHashMap<String,Candidate>()
        for(depth in minOf(3,history.size) downTo 1) {
            val key=history.takeLast(depth).joinToString("|")
            for((word,count) in transitions[key].orEmpty()) {
                if(stem.isNotEmpty() && !word.startsWith(stem))continue
                if(word==stem)continue
                val base=when(depth){3->365;2->270;else->155}
                val score=base+count.coerceAtMost(12)*12
                if(score>(output[word]?.score?:0))
                    output[word]=Candidate(word,score,depth)
            }
        }
        // General linguistic frames complement the fixed cold-start
        // examples.  This generates alternatives for previously unseen
        // subjects and objects (e.g. "keep our team posted") while leaving
        // personalization to the encrypted on-device model.
        for(item in KeyPopLinguisticContinuations.ranked(language,before,fragment)) {
            if(item.score>(output[item.word]?.score?:0))
                output[item.word]=Candidate(item.word,item.score,3)
        }
        return output.values.sortedWith(compareByDescending<Candidate>{it.score}
            .thenBy{it.word}).take(limit.coerceIn(1,24))
    }
    fun suggest(language:Int,before:String,limit:Int=6):List<String> =
        ranked(language,before,limit=limit).map{it.word}
}
