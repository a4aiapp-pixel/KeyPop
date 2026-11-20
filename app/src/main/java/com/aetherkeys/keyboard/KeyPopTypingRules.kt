package com.aetherkeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Pure policy for keyboard assistance; shared by IME and regression tests. */
object KeyPopTypingRules {
    fun textAllowsAutomaticChanges(inputType:Int):Boolean {
        val kind=inputType and InputType.TYPE_MASK_CLASS
        if(kind!=InputType.TYPE_CLASS_TEXT)return false
        if(inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0)return false
        val variation=inputType and InputType.TYPE_MASK_VARIATION
        return variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_PHONETIC
        )
    }
    /**
     * Capitalization is independent from suggestions/autocorrection. Chrome and
     * other ordinary editors may set NO_SUGGESTIONS; that is not a request for
     * lowercase. Still exclude password, email, URL and non-text input types.
     */
    fun textAllowsCapitalization(inputType:Int):Boolean {
        if(inputType and InputType.TYPE_MASK_CLASS!=InputType.TYPE_CLASS_TEXT)return false
        return (inputType and InputType.TYPE_MASK_VARIATION) !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_PHONETIC
        )
    }
    /**
     * An editor can briefly return null for getTextBeforeCursor() during its
     * first focus. Treat that as an empty field ONLY when both initial
     * selection positions are confirmed zero; never guess for existing text.
     */
    fun shouldAutoCapitalize(inputType:Int,before:String?,enabled:Boolean,
                             initialSelStart:Int=-1,initialSelEnd:Int=-1,
                             sentencesEnabled:Boolean=enabled):Boolean {
        if(!textAllowsCapitalization(inputType))return false
        // Newly focused fields may not yet supply a readable text snapshot.
        // Only a verified cursor at zero permits the first-letter fallback.
        if(before==null)return enabled && initialSelStart==0 && initialSelEnd==0
        val context=before.trimEnd { it==' ' || it=='\t' || it=='\r' }
        if(context.isEmpty())return enabled
        if(context.last()=='\n')
            return sentencesEnabled || (enabled && context.all(Char::isWhitespace))
        return sentencesEnabled && context.last() in setOf('.','!','?','؟','۔')
    }

    fun textAllowsSuggestions(inputType:Int,imeOptions:Int=0):Boolean {
        if(inputType and InputType.TYPE_MASK_CLASS!=InputType.TYPE_CLASS_TEXT)return false
        return when(inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PHONETIC -> false
            InputType.TYPE_TEXT_VARIATION_URI ->
                imeOptions and EditorInfo.IME_MASK_ACTION==EditorInfo.IME_ACTION_SEARCH ||
                    inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS!=0
            else -> true
        }
    }

    fun isAddressEditor(inputType:Int):Boolean =
        inputType and InputType.TYPE_MASK_CLASS==InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_MASK_VARIATION==InputType.TYPE_TEXT_VARIATION_URI

    /** A combined search/address box can predict words in a query without touching a URL. */
    fun isAddressToken(before:String):Boolean =
        before.takeLastWhile{!it.isWhitespace()}.any{it in "./:@?#%\\=&+"}

    fun textAllowsLearning(inputType:Int):Boolean =
        textAllowsAutomaticChanges(inputType)

    /**
     * An explicit SEARCH action can make a mixed browser address/search field
     * behave as an ordinary search query. Do not weaken password, email,
     * phone, or URL protection: the IME still checks actual surrounding
     * text for URL/email patterns before attempting an edit.
     */
    fun autocorrectEnabled(toggle:Boolean,inputType:Int,
                           imeOptions:Int=0,allowSearch:Boolean=false):Boolean {
        if(!toggle)return false
        if(textAllowsAutomaticChanges(inputType))return true
        if(!allowSearch || (imeOptions and EditorInfo.IME_MASK_ACTION)!=
            EditorInfo.IME_ACTION_SEARCH)return false
        if((inputType and InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT)
            return false
        val variation=inputType and InputType.TYPE_MASK_VARIATION
        return variation==InputType.TYPE_TEXT_VARIATION_NORMAL ||
            variation==InputType.TYPE_TEXT_VARIATION_URI
    }
    fun autoPunctuationEnabled(toggle:Boolean,inputType:Int):Boolean =
        toggle && textAllowsAutomaticChanges(inputType)
    /** Undo only the just-applied correction, never arbitrary editor text.
     * The editor and suffix are checked by the IME before applying this. */
    fun canUndoRecentCorrection(before:String,original:String,corrected:String,
                                elapsedMs:Long):Boolean =
        original.isNotEmpty() && corrected.isNotEmpty() &&
            elapsedMs in 0L..5000L &&
            before.endsWith(corrected+" ")
    fun preservesCase(original:String,corrected:String):String {
        if(original.isEmpty())return corrected
        return when {
            original.length>1 && original.all{!it.isLetter()||it.isUpperCase()} ->
                corrected.uppercase()
            original.first().isUpperCase() ->
                corrected.replaceFirstChar{it.uppercase()}
            else -> corrected
        }
    }
}
