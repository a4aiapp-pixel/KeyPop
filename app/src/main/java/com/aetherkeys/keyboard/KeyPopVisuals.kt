package com.aetherkeys.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.min

data class KeyPopSkin(
    val name: String, val subtitle: String,
    val background: Int, val surface: Int, val key: Int, val accent: Int,
    val ink: Int, val shape: Int, val outline: Boolean = false,
    val treatment:Int = 0, val font:String = "sans-serif-medium",
    val lift:Int = 2, val gap:Int = 0
)

object KeyPopSkins {
    // Ten original styles and five familiar-layout-inspired styling profiles.
    // Inspired styles are not copies or endorsements of third party products.
    // Index-stable migration; preserve existing Google/Microsoft/Samsung/AMOLED selections.
    val all = listOf(
        KeyPopSkin("Liquid Titanium","Satin metal floating keys",0xFF101922.toInt(),0xFF273846.toInt(),0xFF41515D.toInt(),0xFF8AE4E0.toInt(),0xFFF4FAFC.toInt(),0,false,0,"sans-serif-medium",2,1),
        KeyPopSkin("Crystal Frost","Luminous translucent glass",0xFFEAF5FA.toInt(),0xFFBFD8E8.toInt(),0xFFF9FEFF.toInt(),0xFF197EB4.toInt(),0xFF1C3A50.toInt(),1,true,1,"sans-serif-light",3,2),
        KeyPopSkin("Obsidian Edge","Architectural graphite geometry",0xFF0B0E13.toInt(),0xFF181C26.toInt(),0xFF252B37.toInt(),0xFFB9E34E.toInt(),0xFFF3F5F7.toInt(),2,true,2,"sans-serif-medium",0,1),
        KeyPopSkin("Neon Circuit","Electric precision grid",0xFF07121E.toInt(),0xFF101F30.toInt(),0xFF172B40.toInt(),0xFF33EAC4.toInt(),0xFFE8FFFC.toInt(),2,true,3,"sans-serif-condensed",1,1),
        KeyPopSkin("Soft Clay","Warm sculpted tactile keys",0xFFF5E9DF.toInt(),0xFFE9D6C6.toInt(),0xFFFFF4E9.toInt(),0xFFB35C5E.toInt(),0xFF523C40.toInt(),0,false,4,"sans-serif",3,2),
        KeyPopSkin("Editorial Cream","Elegant minimal typography",0xFFF7F4EC.toInt(),0xFFE9E4D9.toInt(),0xFFFEFDF8.toInt(),0xFF315F5A.toInt(),0xFF26332F.toInt(),2,false,5,"sans-serif",0,0),
        KeyPopSkin("Iridescent Mesh","Violet and blue layered lighting",0xFF14112F.toInt(),0xFF382C6A.toInt(),0xFF4C4586.toInt(),0xFFE8B1FF.toInt(),0xFFFCF5FF.toInt(),0,false,6,"sans-serif-light",3,2),
        KeyPopSkin("Carbon Pro","Precision textured carbon",0xFF121417.toInt(),0xFF262C32.toInt(),0xFF383E45.toInt(),0xFFFF846F.toInt(),0xFFF8FAFB.toInt(),2,true,7,"sans-serif-condensed",1,0),
        KeyPopSkin("Sea Glass","Coastal jade fluid glass",0xFF073E43.toInt(),0xFF17676C.toInt(),0xFF318187.toInt(),0xFFB6FFDB.toInt(),0xFFFFFFFF.toInt(),1,false,8,"sans-serif-light",2,1),
        KeyPopSkin("AMOLED Black","Pure black OLED",0xFF000000.toInt(),0xFF11111A.toInt(),0xFF20202D.toInt(),0xFF9F86FF.toInt(),0xFFFFFFFF.toInt(),2,true,9,"sans-serif-medium",0,0),
        KeyPopSkin("Gboard-style · Material Teal Glass","Dark teal high definition keycaps",0xFF003D39.toInt(),0xFF0B514A.toInt(),0xFF29625F.toInt(),0xFF079E91.toInt(),0xFFF4FFFD.toInt(),1,false,10,"sans-serif-medium",0,0),
        KeyPopSkin("SwiftKey-style · Flow Crystal","Deep blue luminous glass",0xFF0E1E32.toInt(),0xFF203E5A.toInt(),0xFF3E607C.toInt(),0xFF59C6FF.toInt(),0xFFFFFFFF.toInt(),0,false,11,"sans-serif-medium",2,0),
        KeyPopSkin("Samsung-style · One UI Glass","Structured graphite glass keys",0xFF151A22.toInt(),0xFF293646.toInt(),0xFF475769.toInt(),0xFF9ED1FE.toInt(),0xFFFFFFFF.toInt(),2,false,12,"sans-serif-medium",2,0),
        KeyPopSkin("Floating Islands","Separated elevated capsule keys",0xFFF2F4FA.toInt(),0xFFD8DFEE.toInt(),0xFFFFFFFF.toInt(),0xFF575AC3.toInt(),0xFF242A57.toInt(),0,false,13,"sans-serif",6,3),
        KeyPopSkin("Ink Contrast","Strict monochrome flat geometry",0xFFF9F9F7.toInt(),0xFFE6E6E4.toInt(),0xFF202124.toInt(),0xFF545454.toInt(),0xFFFFFFFF.toInt(),2,false,14,"sans-serif-medium",0,0)
    )
    fun index(context: Context) = context.getSharedPreferences("keypop", Context.MODE_PRIVATE)
        .getInt("theme", 0).coerceIn(0, all.lastIndex)
    fun active(context: Context): KeyPopSkin {
        val prefs=context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
        val original=all[index(context)]
        val themed=if(prefs.contains("customAccent"))
            original.copy(accent=prefs.getInt("customAccent",original.accent)) else original
        if(!prefs.getBoolean("highContrast",false))return themed
        // Contrast mode improves legibility without changing profile identity.
        val bg=themed.background
        val luminance=android.graphics.Color.luminance(bg)
        val ink=if(luminance>0.45)Color.BLACK else Color.WHITE
        return themed.copy(ink=ink,outline=true)
    }
    fun radius(skin: KeyPopSkin, user: Int): Float = when (skin.treatment) {
        1,4 -> (user*1.5f).coerceIn(12f,31f)
        2,3,7 -> (user*.55f).coerceIn(3f,10f)
        5 -> (user*.35f).coerceIn(2f,6f)
        6,8,13 -> (user*1.8f).coerceIn(16f,34f)
        14 -> (user*.18f).coerceIn(1f,4f)
        else -> when(skin.shape) {
            0 -> user.toFloat()*1.35f
            1 -> user.toFloat()
            2 -> min(6,user).toFloat()
            3 -> min(10,user).toFloat()
            else -> 1f
        }
    }

}

class KeyboardPreview(context: Context, initial: Int = 0): View(context) {
    var selection: Int = initial
        set(value) { field = value.coerceIn(0, KeyPopSkins.all.lastIndex); invalidate() }
    var previewAccent:Int?=null
        set(value){field=value;invalidate()}
    var ignoreStoredAccent:Boolean=false
        set(value){field=value;invalidate()}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(n: Float) = n * resources.displayMetrics.density
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(136f).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val active=KeyPopSkins.index(context)
        val base=if(selection==active && !ignoreStoredAccent)
            KeyPopSkins.active(context) else KeyPopSkins.all[selection]
        val s=previewAccent?.let{base.copy(accent=it)} ?: base
        val w = width.toFloat()
        val h = height.toFloat()
        KeyPopBackdropDrawable(selection,s).apply {
            if(selection==KeyPopSkins.index(context) &&
                context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                    .getBoolean("animatedThemes",false) &&
                android.animation.ValueAnimator.areAnimatorsEnabled()) {
                phase=(android.os.SystemClock.uptimeMillis()%8000L)/8000f
            }
            setBounds(0,0,width,height)
        }.draw(canvas)
        if(selection==KeyPopSkins.index(context) && isShown &&
            context.getSharedPreferences("keypop",Context.MODE_PRIVATE)
                .getBoolean("animatedThemes",false))
            postInvalidateDelayed(100L)
        paint.shader = null
        val topY = dp(10f)
        paint.color = s.accent
        paint.textSize = dp(10f)
        paint.isFakeBoldText = true
        canvas.drawText("✦  " + s.name.uppercase(), dp(12f), topY + dp(9f), paint)
        paint.isFakeBoldText = false
        val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
        val top = dp(28f)
        val gap = dp(3f)
        val rowHeight = (h - top - dp(9f)) / 3f
        for (i in rows.indices) {
            val row = rows[i]
            val side = if (i == 0) dp(9f) else if (i == 1) dp(22f) else dp(39f)
            val widthAvailable = w - side * 2
            val kw = (widthAvailable - gap * (row.length - 1)) / row.length
            for (j in row.indices) {
                val x = side + j * (kw + gap)
                val y = top + i * rowHeight
                val rect = RectF(x, y, x + kw, y + rowHeight - gap)
                // Actual runtime keycap renderer, so previews cannot misrepresent a theme.
                KeyPopGlass.key(context,s,false,selection).apply {
                    setBounds(rect.left.toInt(),rect.top.toInt(),rect.right.toInt(),rect.bottom.toInt())
                }.draw(canvas)
                paint.style=Paint.Style.FILL
                paint.color = s.ink
                paint.typeface=android.graphics.Typeface.create(s.font,android.graphics.Typeface.NORMAL)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = dp(10f)
                canvas.drawText(row[j].toString(), x + kw / 2, y + (rowHeight - gap) * .64f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
        }
    }
}


/** Resolution independent illustrated themes. Drawn once when the keyboard is invalidated. */
class KeyPopBackdropDrawable(private val index:Int,private val skin:KeyPopSkin):
    android.graphics.drawable.Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var phase:Float=0f
        set(value){field=value.coerceIn(0f,1f);invalidateSelf()}
    private fun wave():Float=
        0.5f + kotlin.math.sin(phase.toDouble()*6.283185307179586).toFloat()*0.5f
    private fun alpha(color:Int,a:Int)=(color and 0x00FFFFFF) or (a.coerceIn(0,255) shl 24)
    override fun draw(canvas:Canvas) {
        val rect=bounds
        val w=rect.width().toFloat();val h=rect.height().toFloat()
        if(w<=0f||h<=0f)return
        canvas.save()
        canvas.translate(rect.left.toFloat(),rect.top.toFloat())
        p.style=Paint.Style.FILL
        if(index==9 || index==14) {
            p.color=skin.background;canvas.drawRect(0f,0f,w,h,p)
        } else {
            p.shader=LinearGradient(0f,0f,w,h,skin.background,skin.surface,Shader.TileMode.CLAMP)
            canvas.drawRect(0f,0f,w,h,p);p.shader=null
        }
        val accent=skin.accent
        when(index) {
            0 -> {
                p.style=Paint.Style.STROKE;p.strokeWidth=1.2f;p.color=alpha(accent,26)
                for(i in 0..16){val x=i*w/16f;canvas.drawLine(x,0f,x+w*.12f,h,p)}
                p.style=Paint.Style.FILL
            }
            1 -> {
                p.shader=android.graphics.RadialGradient(w*.8f,0f,h*.9f,
                    intArrayOf(alpha(Color.WHITE,150),alpha(Color.WHITE,0)),null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,w,h,p);p.shader=null
            }
            2,5 -> {
                p.style=Paint.Style.STROKE;p.strokeWidth=1f
                p.color=alpha(accent,35);canvas.drawLine(0f,2f,w,2f,p)
                p.style=Paint.Style.FILL
            }
            3 -> {
                p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=alpha(accent,32)
                for(i in 0..16){val x=i*w/16f;canvas.drawLine(x,0f,x,h,p)}
                for(i in 0..8){val y=i*h/8f;canvas.drawLine(0f,y,w,y,p)}
                p.style=Paint.Style.FILL
            }
            4,13 -> {
                p.shader=android.graphics.RadialGradient(w*.22f,0f,h*.94f,
                    intArrayOf(alpha(Color.WHITE,140),alpha(Color.WHITE,0)),null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,w,h,p);p.shader=null
            }
            6 -> {
                p.shader=android.graphics.RadialGradient(w*.8f,h*.17f,h*.94f,
                    intArrayOf(alpha(0xFFC37AFF.toInt(),90),alpha(accent,0)),null,Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,w,h,p);p.shader=null
            }
            7 -> {
                p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=alpha(Color.WHITE,14)
                for(i in 0..28){val x=i*w/28f;canvas.drawLine(x,0f,x+w*.12f,h,p)}
                p.style=Paint.Style.FILL
            }
            8,11 -> {
                p.style=Paint.Style.STROKE;p.strokeWidth=5f
                for(i in 0..2) {
                    p.color=alpha(accent,14+i*10)
                    val path=android.graphics.Path().apply {
                        moveTo(-20f,h*(.2f+i*.24f))
                        cubicTo(w*.3f,h*(.1f+i*.22f),w*.72f,h*(.4f+i*.1f),
                            w+20f,h*(.2f+i*.16f))
                    }
                    canvas.drawPath(path,p)
                }
                p.style=Paint.Style.FILL
            }
            9 -> Unit // Preserve true-black OLED: no dots, grid or bloom
            10 -> {
                p.color=alpha(0xFFB8FFF0.toInt(),27)
                canvas.drawRect(0f,0f,w,1.5f,p)
            }
            12,14 -> Unit
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int){p.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(filter:android.graphics.ColorFilter?){p.colorFilter=filter;invalidateSelf()}
    @Deprecated("Deprecated in Java")
    override fun getOpacity():Int=android.graphics.PixelFormat.TRANSLUCENT
}
