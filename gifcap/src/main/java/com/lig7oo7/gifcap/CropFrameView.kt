package com.lig7oo7.gifcap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * 반투명 크롭 프레임. 조정 모드에서는 안쪽을 끌면 이동, 오른쪽 아래 손잡이를 끌면 크기 조절.
 * 고정 모드에서는 터치가 아래 앱(카톡)으로 그대로 통과한다.
 */
@SuppressLint("ViewConstructor")
class CropFrameView(
    ctx: Context,
    private val wm: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onChanged: () -> Unit
) : View(ctx) {

    private val dp = ctx.resources.displayMetrics.density
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = 0xFF00BFA5.toInt()
    }
    private val fill = Paint().apply { color = 0x2200BFA5 }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00BFA5.toInt() }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12 * dp
        setShadowLayer(3 * dp, 0f, 0f, 0xFF000000.toInt())
    }

    var adjusting = true
        set(v) {
            field = v
            border.pathEffect = if (v) null else DashPathEffect(floatArrayOf(8 * dp, 6 * dp), 0f)
            invalidate()
        }

    private var mode = 0   // 1 이동, 2 크기
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var startW = 0
    private var startH = 0
    private val minSize get() = (60 * dp).toInt()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val half = border.strokeWidth / 2
        if (adjusting) c.drawRect(0f, 0f, w, h, fill)
        c.drawRect(half, half, w - half, h - half, border)
        if (adjusting) {
            c.drawCircle(w - 14 * dp, h - 14 * dp, 11 * dp, handle)
            c.drawText("끌어서 이동 · ◢ 크기", 8 * dp, 18 * dp, label)
            c.drawText("${width}×${height}", 8 * dp, h - 10 * dp, label)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!adjusting) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                startX = params.x; startY = params.y
                startW = params.width; startH = params.height
                val nearHandle = e.x > width - 48 * dp && e.y > height - 48 * dp
                mode = if (nearHandle) 2 else 1
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - downX).toInt()
                val dy = (e.rawY - downY).toInt()
                if (mode == 1) {
                    params.x = startX + dx
                    params.y = startY + dy
                } else {
                    params.width = maxOf(minSize, startW + dx)
                    params.height = maxOf(minSize, startH + dy)
                }
                wm.updateViewLayout(this, params)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = 0
                onChanged()
            }
        }
        return true
    }
}
