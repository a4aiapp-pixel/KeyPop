package com.aetherkeys.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class AetherImeService : InputMethodService() {
    private val prefs by lazy { getSharedPreferences("keypop",Context.MODE_PRIVATE) }
    private val clipStore by lazy { ClipStore(this) }
    private val clipboard by lazy { getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val languages=listOf("ENGLISH","اردو","سنڌي")
    private var language=0
    private var shift=false
    private var symbols=false
    private var clipsOpen=false
    private var compact=false
    private var current:LinearLayout?=null
    private val adaptive by lazy { KeyPopAdaptiveServiceController(applicationContext) }
    private var suggestionChips:List<Button> = emptyList()
    private var displayedCandidates:List<KeyPopAdaptiveLanguageModel.Candidate> = emptyList()
    private var editorEpoch=0L
    private val english=listOf(listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"))
    private val urdu=listOf(listOf("ق","و","ع","ر","ت","ے","ء","ی","ہ","پ"),
        listOf("ا","س","د","ف","گ","ح","ج","ک","ل"),
        listOf("ز","ش","چ","ط","ب","ن","م"))
    private val sindhi=listOf(listOf("ق","و","ع","ر","ت","ي","ء","ڀ","ه","پ"),
        listOf("ا","س","د","ف","ڳ","ح","ج","ڪ","ل"),
        listOf("ز","ش","چ","ٽ","ٻ","ن","م"))
    private val symbolRows=listOf(listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","-","+","(",")","/"),
        listOf("*","\"","'",";",":","!","?","_","="))

    override fun onCreate() {
        super.onCreate()
        clipboard.addPrimaryClipChangedListener {
            val info=currentInputEditorInfo
            val type=info?.inputType
            val klass=type?.and(InputType.TYPE_MASK_CLASS)
            val variation=type?.and(InputType.TYPE_MASK_VARIATION)
            val sensitive=info==null || klass==InputType.TYPE_NULL ||
                ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0) ||
                (klass==InputType.TYPE_CLASS_TEXT &&
                    (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                     variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                     variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (klass==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            if (!sensitive && prefs.getBoolean("history",true)) {
                try {
                    val clip=clipboard.primaryClip
                    if(clip!=null && clip.itemCount>0 && clip.description.hasMimeType("text/plain")) {
                        if(Build.VERSION.SDK_INT>=33 &&
                            clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE")==true)
                            return@addPrimaryClipChangedListener
                        clip.getItemAt(0).text?.toString()?.let { clipStore.save(it) }
                    }
                } catch (_:Exception) { /* Never break typing on inaccessible clipboard. */ }
            }
        }
    }

    override fun onCreateInputView():View {
        language=prefs.getInt("language",0).coerceIn(0,2)
        current=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        draw()
        return current!!
    }
    override fun onStartInputView(info:EditorInfo?,restarting:Boolean) {
        super.onStartInputView(info,restarting)
        editorEpoch++
        adaptive.invalidateSession()
        draw()
        requestAdaptivePrediction()
    }

    override fun onFinishInputView(finishingInput:Boolean) {
        editorEpoch++
        adaptive.invalidateSession()
        displayedCandidates=emptyList()
        suggestionChips=emptyList()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart:Int,oldSelEnd:Int,
        newSelStart:Int,newSelEnd:Int,candidatesStart:Int,candidatesEnd:Int) {
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,
            candidatesStart,candidatesEnd)
        if(oldSelStart!=newSelStart || oldSelEnd!=newSelEnd) {
            adaptive.invalidateSession()
            requestAdaptivePrediction()
        }
    }

    override fun onDestroy() {
        if(this::class.java!=null) {
            // A lazy service controller may be initialized by the first view.
            runCatching { adaptive.close() }
        }
        super.onDestroy()
    }

    private fun d(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun gradient(a:Int,b:Int,r:Int,stroke:Int?=null):GradientDrawable=
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply {
            cornerRadius=d(r).toFloat()
            if(stroke!=null) setStroke(d(1),stroke)
        }
    private fun palette()=KeyPopSkins.active(this)
    private fun tapFeedback(v:View) {
        if(prefs.getBoolean("haptics",false)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if(prefs.getBoolean("sound",false))
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK)
    }
    private fun makeKey(label:String,heavy:Boolean=false,weight:Float=1f,action:()->Unit):Button {
        val s=palette()
        val round=KeyPopSkins.radius(s,prefs.getInt("round",13)).toInt().coerceIn(1,40)
        val bg=if(heavy) gradient(s.accent,s.surface,round,s.accent)
            else gradient(s.key,s.surface,round,if(s.outline) s.accent else null)
        return Button(this).apply {
            text=label
            textSize=if(label.length>3) 12f else prefs.getInt("font",17).coerceIn(12,24).toFloat()
            isAllCaps=false
            setTextColor(if(heavy) Color.WHITE else s.ink)
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            gravity=Gravity.CENTER
            background=bg
            minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
            setPadding(0,0,0,0)
            elevation=d(if(s.shape==2) 0 else 2).toFloat()
            isFocusable=false
            isFocusableInTouchMode=false
            setOnClickListener { tapFeedback(this); action() }
        }
    }
    private fun chip(label:String,weight:Float=1f,onTap:()->Unit):Button {
        val s=palette()
        return Button(this).apply {
            text=label; textSize=11f; isAllCaps=false
            setTextColor(s.ink); minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
            background=gradient(s.surface,s.background,11,if(s.outline) s.accent else null)
            setPadding(0,0,0,0)
            isFocusable=false
            isFocusableInTouchMode=false
            setOnClickListener { tapFeedback(this); onTap() }
        }
    }
    private fun draw() {
        val root=current ?: return
        root.removeAllViews()
        val s=palette()
        root.background=gradient(s.background,s.surface,0)
        root.setPadding(d(3),d(5),d(3),d(6))
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        fun topButton(value:String,weight:Float=1f,callback:()->Unit) {
            top.addView(chip(value,weight,callback),LinearLayout.LayoutParams(0,d(36),weight).apply {
                leftMargin=d(2); rightMargin=d(2)
            })
        }
        topButton("✦  "+languages[language],1.5f) { cycleLanguage() }
        topButton("▤ Clips",1.05f) { clipsOpen=!clipsOpen; draw() }
        topButton(if(symbols) "ABC" else "?#@",.82f) { symbols=!symbols;clipsOpen=false;draw() }
        topButton(if(compact) "↔" else "◫",.58f) { compact=!compact;draw() }
        topButton("◈",.55f) { prefs.edit().putInt("theme",(KeyPopSkins.index(this)+1)%15).apply();draw() }
        topButton("★",.50f) { rememberCurrentPhrase() }
        topButton("⚙",.55f) {
            startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        root.addView(top)
        suggestionChips=emptyList()
        displayedCandidates=emptyList()
        if(clipsOpen) { drawClips(root); return }
        val suggestions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        suggestionChips=(0 until 3).map { index ->
            chip(" ") { acceptAdaptiveCandidate(index) }.also { button ->
                button.isEnabled=false
                button.maxLines=1
                button.ellipsize=TextUtils.TruncateAt.END
                suggestions.addView(button,LinearLayout.LayoutParams(0,d(40),1f).apply {
                    leftMargin=d(2);rightMargin=d(2)
                })
            }
        }
        root.addView(suggestions)
        requestAdaptivePrediction()
        val title=TextView(this).apply {
            text=if(symbols) "SYMBOLS   •   TAP ABC TO RETURN"
                 else if(language==0) "✧  KEYPOP  •  TYPE YOUR WAY"
                 else "✧  KEYPOP  •  "+languages[language]
            textSize=9.5f; letterSpacing=0.07f
            setTextColor(s.accent)
            gravity=Gravity.CENTER_VERTICAL
            setPadding(d(10),0,0,0)
        }
        root.addView(title,LinearLayout.LayoutParams(-1,d(25)))
        val rows=if(symbols) symbolRows else when(language) { 1->urdu;2->sindhi;else->english }
        val keyHeight=prefs.getInt("height",51).coerceIn(36,90)
        if(!symbols && prefs.getBoolean("numberRow",true)) addRow(root, listOf("1","2","3","4","5","6","7","8","9","0"),keyHeight-10)
        rows.forEachIndexed { i, row ->
            val keys=if(i==rows.lastIndex && !symbols) listOf("⇧")+row+listOf("⌫") else row
            addRow(root,keys,keyHeight)
        }
        addRow(root,listOf(if(symbols) "ABC" else "?123", "🌐", ",","SPACE",".","↵","⌫"),keyHeight, true)
    }
    private fun addRow(parent:LinearLayout,labels:List<String>,height:Int,footer:Boolean=false) {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        val gap=prefs.getInt("spacing",4).coerceIn(1,10)
        if(compact) row.setPadding(d(28),0,d(3),0)
        labels.forEach { label ->
            val show=when {
                label=="SPACE" -> if(language==0) "English" else languages[language]
                shift && language==0 && label.length==1 && label[0].isLetter() -> label.uppercase(Locale.ROOT)
                else -> label
            }
            val weight=when(label) {
                "SPACE" -> 4.3f
                "?123","ABC" -> 1.4f
                "🌐" -> 1.2f
                else -> 1f
            }
            val heavy=label in listOf("↵","⇧") || (label=="SPACE" && palette().shape==3)
            val button=makeKey(show,heavy,weight) { press(label) }
            val alternate=alternate(label)
            if(alternate!=null) button.setOnLongClickListener {
                tapFeedback(button)
                currentInputConnection?.commitText(alternate,1)
                true
            }
            row.addView(button,LinearLayout.LayoutParams(0,d(height),weight).apply {
                leftMargin=d(gap/2); rightMargin=d(gap/2); topMargin=d(2); bottomMargin=d(2)
            })
        }
        parent.addView(row)
    }
    private fun alternate(label:String):String? {
        val numeric="qwertyuiop".indexOf(label)
        if(numeric>=0 && prefs.getBoolean("hints",true)) return if(numeric==9) "0" else (numeric+1).toString()
        return mapOf("a" to "@","s" to "$","d" to "#","f" to "%","g" to "&",
            "n" to "ñ","e" to "é","." to "…","," to ";","⌫" to "").get(label)
    }
    private fun press(value:String) {
        when(value) {
            "⌫" -> {
                val connection=currentInputConnection
                val selected=runCatching { connection?.getSelectedText(0)?.isNotEmpty()==true }
                    .getOrDefault(false)
                if(selected) connection?.commitText("",1)
                else if(connection?.deleteSurroundingTextInCodePoints(1,0)!=true) {
                    if(connection?.deleteSurroundingText(1,0)!=true) {
                        connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
                        connection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL))
                    }
                }
            }
            "⇧" -> { if(language==0) shift=!shift; draw() }
            "SPACE" -> currentInputConnection?.commitText(" ",1)
            "🌐" -> cycleLanguage()
            "?123" -> { symbols=true;draw() }
            "ABC" -> { symbols=false;draw() }
            "↵" -> {
                val info=currentInputEditorInfo
                val action=(info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                val whatsapp=info?.packageName in setOf("com.whatsapp","com.whatsapp.w4b")
                val multiline=(info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE!=0
                if(whatsapp || multiline ||
                    action in setOf(EditorInfo.IME_ACTION_NONE,EditorInfo.IME_ACTION_UNSPECIFIED))
                    currentInputConnection?.commitText("\n",1)
                else currentInputConnection?.performEditorAction(action)
            }
            else -> {
                currentInputConnection?.commitText(
                    if(shift && language==0) value.uppercase(Locale.ROOT) else value,1)
                if(shift && language==0) { shift=false;draw() }
            }
        }
        requestAdaptivePrediction()
    }
    private fun safeEditor():Boolean {
        val info=currentInputEditorInfo ?: return false
        val clazz=info.inputType and InputType.TYPE_MASK_CLASS
        if(clazz!=InputType.TYPE_CLASS_TEXT)return false
        val variation=info.inputType and InputType.TYPE_MASK_VARIATION
        if(variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))return false
        return !symbols && !clipsOpen
    }

    private fun clearSuggestions() {
        displayedCandidates=emptyList()
        suggestionChips.forEach { chip -> chip.isEnabled=false;chip.text=" " }
    }

    private fun requestAdaptivePrediction() {
        val chips=suggestionChips
        if(chips.size!=3)return
        if(!safeEditor()) { clearSuggestions();adaptive.invalidateSession();return }
        val connection=currentInputConnection ?: run { clearSuggestions();return }
        // InputConnection calls must be on the IME thread and restricted to
        // an eligible field. Never inspect secure/number/URI editor contents.
        val before=runCatching {
            connection.getTextBeforeCursor(220,0)?.toString().orEmpty()
        }.getOrDefault("")
        val after=runCatching {
            connection.getTextAfterCursor(48,0)?.toString().orEmpty()
        }.getOrDefault("")
        if(KeyPopUrlEditingPolicy.touchesUrlOrEmail(before,after) ||
            runCatching { connection.getSelectedText(0)?.isNotEmpty()==true }
                .getOrDefault(false)) {
            clearSuggestions();adaptive.invalidateSession();return
        }
        val info=currentInputEditorInfo
        val epoch=editorEpoch
        val pkg=info?.packageName
        clearSuggestions()
        adaptive.predict(before,after,info,false) { candidates ->
            if(epoch!=editorEpoch || !safeEditor() || currentInputEditorInfo?.packageName!=pkg ||
                suggestionChips!==chips)return@predict
            val live=currentInputConnection ?: return@predict
            val liveBefore=runCatching {
                live.getTextBeforeCursor(220,0)?.toString().orEmpty()
            }.getOrDefault("")
            val liveAfter=runCatching {
                live.getTextAfterCursor(48,0)?.toString().orEmpty()
            }.getOrDefault("")
            if(liveBefore!=before || liveAfter!=after)return@predict
            displayedCandidates=candidates.take(3)
            chips.forEachIndexed { index, button ->
                val c=displayedCandidates.getOrNull(index)
                button.text=c?.display ?: " "
                button.isEnabled=c!=null
            }
        }
    }

    private fun acceptAdaptiveCandidate(index:Int) {
        val chosen=displayedCandidates.getOrNull(index) ?:return
        if(!safeEditor())return
        val connection=currentInputConnection ?:return
        if(runCatching { connection.getSelectedText(0)?.isNotEmpty()==true }
                .getOrDefault(false))return
        val before=runCatching {
            connection.getTextBeforeCursor(220,0)?.toString().orEmpty()
        }.getOrDefault("")
        val after=runCatching {
            connection.getTextAfterCursor(48,0)?.toString().orEmpty()
        }.getOrDefault("")
        val plan=KeyPopAdaptiveCandidatePlanner.makePlan(before,after,chosen.replacement)
            ?:return
        if(!before.endsWith(plan.expectedPrefix) || !after.startsWith(plan.expectedSuffix))
            return
        val trailing=if(after.isEmpty())" "
            else if(after.first().isWhitespace() || after.first() in setOf('.',',',';',':','!','?'))
                "" else " "
        val replacement=plan.insert+trailing
        connection.beginBatchEdit()
        var success=false
        try {
            val deleted=plan.replaceBeforeChars==0 ||
                connection.deleteSurroundingText(plan.replaceBeforeChars,0)
            success=deleted && connection.commitText(replacement,1)
        } finally { connection.endBatchEdit() }
        if(success) {
            val word=chosen.replacement.takeWhile { !it.isWhitespace() }
            adaptive.learnAcceptedWord(word,currentInputEditorInfo,false)
            displayedCandidates=emptyList()
            requestAdaptivePrediction()
        }
    }

    private fun rememberCurrentPhrase() {
        if(!prefs.getBoolean("adaptive_finished_phrases",false)) {
            Toast.makeText(this,"Enable finished-phrase memory in Personal Intelligence settings first",
                Toast.LENGTH_SHORT).show()
            return
        }
        if(!safeEditor()) {
            Toast.makeText(this,"Phrase memory is unavailable in this field",
                Toast.LENGTH_SHORT).show()
            return
        }
        val connection=currentInputConnection ?:return
        val source=runCatching {
            connection.getTextBeforeCursor(220,0)?.toString().orEmpty()
        }.getOrDefault("")
        if(source.length < 12 || KeyPopUrlEditingPolicy.touchesUrlOrEmail(source)) {
            Toast.makeText(this,"Enter a complete, non-URL phrase before saving",
                Toast.LENGTH_SHORT).show()
            return
        }
        adaptive.learnConfirmedPhrase(source,currentInputEditorInfo,false)
        Toast.makeText(this,"Phrase learning requested (stored only if eligible)",
            Toast.LENGTH_SHORT).show()
    }

    private fun cycleLanguage() {
        language=(language+1)%3
        prefs.edit().putInt("language",language).apply()
        shift=false;symbols=false;clipsOpen=false;draw()
    }
    private fun drawClips(root:LinearLayout) {
        val s=palette()
        val title=TextView(this).apply {
            text="ENCRYPTED CLIPBOARD  ·  TAP TO PASTE  ·  HOLD TO PIN"
            textSize=10f;setTextColor(s.ink);setPadding(d(9),d(10),0,d(9))
        }
        root.addView(title)
        val items=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val listed=clipStore.list()
        if(listed.isEmpty()) items.addView(TextView(this).apply {
            text="Nothing copied yet. Your saved text will appear here."
            setTextColor(s.ink);setPadding(d(9),d(20),d(9),d(20))
        })
        listed.take(60).forEach { item ->
            val button=makeKey((if(item.pinned) "📌 " else "")+item.text.take(65)) {
                currentInputConnection?.commitText(item.text,1);clipsOpen=false;draw()
            }
            button.setOnLongClickListener { clipStore.pin(item.text,!item.pinned);draw();true }
            items.addView(button,LinearLayout.LayoutParams(-1,d(46)).apply{bottomMargin=d(5)})
        }
        root.addView(ScrollView(this).apply { addView(items) },LinearLayout.LayoutParams(-1,d(215)))
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val back=makeKey("←  Keyboard") { clipsOpen=false;draw() }
        val erase=makeKey("Hold to clear") { Toast.makeText(this,"Hold to clear all history",Toast.LENGTH_SHORT).show() }
        erase.setOnLongClickListener { clipStore.clear();draw();true }
        actions.addView(back,LinearLayout.LayoutParams(0,d(43),1f).apply{rightMargin=d(4)})
        actions.addView(erase,LinearLayout.LayoutParams(0,d(43),1f).apply{leftMargin=d(4)})
        root.addView(actions)
    }
}
