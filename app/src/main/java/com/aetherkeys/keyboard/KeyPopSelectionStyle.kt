package com.aetherkeys.keyboard

import android.content.Context
import android.widget.EditText

/**
 * Selection highlight inside KeyPop-owned editors. Android's InputMethodService
 * cannot change another application's EditText selection or cursor colors.
 */
object KeyPopSelectionStyle {
    const val DEFAULT_COLOR:Int = 0xFF195A91.toInt()
    const val DEFAULT_OPACITY:Int = 88
    const val COLOR_KEY="selectionHighlightColor"
    const val OPACITY_KEY="selectionHighlightOpacity"

    // Pure ARGB implementation also used by JVM regression tests.
    fun withOpacity(color:Int,percent:Int):Int {
        val opacity=((percent.coerceIn(25,100)*255)+50)/100
        return (color and 0x00FFFFFF) or (opacity shl 24)
    }

    fun effective(context:Context):Int {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        return withOpacity(prefs.getInt(COLOR_KEY,DEFAULT_COLOR),
            prefs.getInt(OPACITY_KEY,DEFAULT_OPACITY))
    }

    fun apply(edit:EditText) {
        edit.highlightColor=effective(edit.context)
    }
}
