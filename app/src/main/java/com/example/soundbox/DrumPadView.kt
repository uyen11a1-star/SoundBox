package com.example.soundbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrumPadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onToggle: ((Int, Int) -> Unit)? = null
    var onPreview: ((Int) -> Unit)? = null

    private val numVoices = DrumEngine.NUM_VOICES
    private val numSteps = DrumEngine.NUM_STEPS
    private val labels = arrayOf("Kick", "Snare", "Hi-hat", "Tom")

    private val cellRects = Array(numVoices) { Array(numSteps) { RectF() } }
    private val labelRects = Array(numVoices) { RectF() }

    @Volatile var pattern: Array<BooleanArray> = Array(numVoices) { BooleanArray(numSteps) }
    @Volatile var currentStep: Int = -1

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#CCCCCC"); textSize = 30f; isAntiAlias = true
    }
    private val labelBoldPaint = Paint().apply {
        color = Color.WHITE; textSize = 30f; isAntiAlias = true; isFakeBoldText = true
    }
    private val cellOffPaint = Paint().apply {
        color = Color.parseColor("#2A2A2A"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val cellOnPaint = Paint().apply {
        color = Color.parseColor("#FF9800"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val cellBorderPaint = Paint().apply {
        color = Color.parseColor("#444444"); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val colHighlightPaint = Paint().apply {
        color = Color.parseColor("#33FFEB3B"); style = Paint.Style.FILL; isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout(w.toFloat(), h.toFloat())
    }

    private fun layout(w: Float, h: Float) {
        val labelW = w * 0.16f
        val gridLeft = labelW + 6f
        val gridW = w - gridLeft
        val cellW = gridW / numSteps
        val cellH = h / numVoices
        for (v in 0 until numVoices) {
            labelRects[v] = RectF(0f, v * cellH, labelW, (v + 1) * cellH)
            for (s in 0 until numSteps) {
                val p = 3f
                cellRects[v][s] = RectF(
                    gridLeft + s * cellW + p, v * cellH + p,
                    gridLeft + (s + 1) * cellW - p, (v + 1) * cellH - p
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (currentStep in 0 until numSteps && cellRects[0][0].isNotEmpty()) {
            val left = cellRects[0][currentStep].left - 3f
            val right = cellRects[0][currentStep].right + 3f
            canvas.drawRect(left, 0f, right, height.toFloat(), colHighlightPaint)
        }

        for (v in 0 until numVoices) {
            val r = labelRects[v]
            val txt = labels[v]
            val p = if (v <= DrumEngine.SNARE) labelBoldPaint else labelPaint
            val tw = p.measureText(txt)
            val ty = r.centerY() - (p.descent() + p.ascent()) / 2f
            canvas.drawText(txt, r.left + (r.width() - tw) / 2f, ty, p)
        }

        for (v in 0 until numVoices) {
            for (s in 0 until numSteps) {
                val r = cellRects[v][s]
                val p = if (pattern[v][s]) cellOnPaint else cellOffPaint
                canvas.drawRoundRect(r, 8f, 8f, p)
                canvas.drawRoundRect(r, 8f, 8f, cellBorderPaint)
            }
        }
    }

    private fun hitTest(x: Float, y: Float): Pair<Int, Int>? {
        for (v in 0 until numVoices) {
            if (labelRects[v].contains(x, y)) return Pair(v, -1)
            for (s in 0 until numSteps) {
                if (cellRects[v][s].contains(x, y)) return Pair(v, s)
            }
        }
        return null
    }

    private var downVoice = -1
    private var downStep = -1
    private var labelTouch = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y) ?: return false
                downVoice = hit.first
                downStep = hit.second
                labelTouch = (downStep == -1)
                onPreview?.invoke(downVoice)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!labelTouch && downVoice >= 0 && downStep >= 0) {
                    onToggle?.invoke(downVoice, downStep)
                    invalidate()
                }
                downVoice = -1
                downStep = -1
                labelTouch = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downVoice = -1; downStep = -1; labelTouch = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
