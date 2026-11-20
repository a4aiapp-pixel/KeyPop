package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Non-interactive glide feedback over every theme and split/floating layout.
 * Kept out of IME text storage; all points vanish at gesture completion.
 */
class KeyPopGlideTrailView(context:Context):View(context) {
    private val strokes=Path()
    private val brush=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=5f*resources.displayMetrics.density
        strokeCap=Paint.Cap.ROUND
        strokeJoin=Paint.Join.ROUND
        color=Color.CYAN
        alpha=195
    }
    private val offset=IntArray(2)
    private var tracking=false
    init {
        isClickable=false
        isFocusable=false
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }
    fun begin(x:Float,y:Float,color:Int) {
        brush.color=color
        brush.alpha=195
        strokes.reset()
        tracking=true
        getLocationOnScreen(offset)
        strokes.moveTo(x-offset[0],y-offset[1])
        invalidate()
    }
    fun point(x:Float,y:Float) {
        if(!tracking)return
        getLocationOnScreen(offset)
        strokes.lineTo(x-offset[0],y-offset[1])
        invalidate()
    }
    fun clear() {
        if(!tracking)return
        tracking=false
        strokes.reset()
        invalidate()
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(tracking)canvas.drawPath(strokes,brush)
    }
}
