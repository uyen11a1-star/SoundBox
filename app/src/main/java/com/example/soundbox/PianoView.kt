package com.example.soundbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class PianoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onNoteOn: ((Int) -> Unit)? = null
    var onNoteOff: ((Int) -> Unit)? = null

    // 15 phim trang: C4 -> C6 (2 quang tam)
    // C4=60 D4=62 E4=64 F4=65 G4=67 A4=69 B4=71
    // C5=72 D5=74 E5=76 F5=77 G5=79 A5=81 B5=83 C6=84
    private val whiteNotes = intArrayOf(
        60, 62, 64, 65, 67, 69, 71,
        72, 74, 76, 77, 79, 81, 83,
        84
    )

    // Phim den: dat giua 2 phim trang, sau white index tuong ung
    private data class BlackKey(val afterWhite: Int, val midi: Int)
    private val blackKeys = listOf(
        BlackKey(0, 61), BlackKey(1, 63),
        BlackKey(3, 66), BlackKey(4, 68), BlackKey(5, 70),
        BlackKey(7, 73), BlackKey(8, 75),
        BlackKey(10, 78), BlackKey(11, 80), BlackKey(12, 82)
    )

    private val whitePaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true
    }
    private val whitePressedPaint = Paint().apply {
        color = Color.parseColor("#FFD54F"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val blackPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val blackPressedPaint = Paint().apply {
        color = Color.parseColor("#FF8F00"); style = Paint.Style.FILL; isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#666666"); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }

    private val whiteRects = ArrayList<RectF>()
    private val blackRects = ArrayList<RectF>()

    private val pointerToNote = HashMap<Int, Int>()
    private val pressedNotes = HashSet<Int>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys(w.toFloat(), h.toFloat())
    }

    private fun layoutKeys(w: Float, h: Float) {
        whiteRects.clear()
        blackRects.clear()
        val nWhite = whiteNotes.size
        val ww = w / nWhite
        for (i in 0 until nWhite) {
            whiteRects.add(RectF(i * ww, 0f, (i + 1) * ww, h))
        }
        val bw = ww * 0.62f
        val bh = h * 0.62f
        for (bk in blackKeys) {
            val cx = (bk.afterWhite + 1) * ww
            blackRects.add(RectF(cx - bw / 2f, 0f, cx + bw / 2f, bh))
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (i in whiteRects.indices) {
            val note = whiteNotes[i]
            val p = if (pressedNotes.contains(note)) whitePressedPaint else whitePaint
            canvas.drawRect(whiteRects[i], p)
            canvas.drawRect(whiteRects[i], borderPaint)
        }
        for (i in blackRects.indices) {
            val note = blackKeys[i].midi
            val p = if (pressedNotes.contains(note)) blackPressedPaint else blackPaint
            canvas.drawRect(blackRects[i], p)
            canvas.drawRect(blackRects[i], borderPaint)
        }
    }

    private fun hitTest(x: Float, y: Float): Int? {
        for (i in blackRects.indices) {
            if (blackRects[i].contains(x, y)) return blackKeys[i].midi
        }
        for (i in whiteRects.indices) {
            if (whiteRects[i].contains(x, y)) return whiteNotes[i]
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val note = hitTest(event.getX(idx), event.getY(idx))
                if (note != null) {
                    pointerToNote[pid] = note
                    if (pressedNotes.add(note)) onNoteOn?.invoke(note)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val newNote = hitTest(event.getX(i), event.getY(i))
                    val oldNote = pointerToNote[pid]
                    if (newNote != oldNote) {
                        if (oldNote != null && !pointerToNote.containsValue(oldNote)) {
                            pressedNotes.remove(oldNote)
                            onNoteOff?.invoke(oldNote)
                        }
                        if (newNote != null) {
                            pointerToNote[pid] = newNote
                            if (pressedNotes.add(newNote)) onNoteOn?.invoke(newNote)
                        } else {
                            pointerToNote.remove(pid)
                        }
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val note = pointerToNote.remove(pid)
                if (note != null && !pointerToNote.containsValue(note)) {
                    pressedNotes.remove(note)
                    onNoteOff?.invoke(note)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                for (n in pointerToNote.values) {
                    pressedNotes.remove(n)
                    onNoteOff?.invoke(n)
                }
                pointerToNote.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
