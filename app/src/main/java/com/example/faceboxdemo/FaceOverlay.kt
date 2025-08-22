package com.example.faceboxdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    @Volatile
    private var boxes: List<RectF> = emptyList()

    fun setBoxes(rects: List<RectF>) {
        boxes = rects
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (r in boxes) {
            canvas.drawRoundRect(r, 20f, 20f, paint)
        }
    }
}
