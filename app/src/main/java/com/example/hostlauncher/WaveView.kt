package com.example.hostlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * A circular "water ripple" view: two sine waves endlessly scrolling inside a circle,
 * conveying that an operation is in progress without reporting a real percentage.
 * The water level is fixed (~55%); only the ripples move.
 */
class WaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaintBack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val clipPath = Path()
    private val wavePath = Path()

    private var phase = 0f
    private var animator: ValueAnimator? = null

    init {
        var base = Color.parseColor("#0B5FFF")
        val tv = TypedValue()
        if (context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, tv, true)) {
            base = tv.data
        }
        val rgb = base and 0x00FFFFFF
        wavePaint.color = rgb or (0xB3 shl 24)      // ~70% alpha, front wave
        wavePaintBack.color = rgb or (0x66 shl 24)  // ~40% alpha, back wave
        ringPaint.color = base
        ringPaint.strokeWidth = dp(3f)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1400L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = min(w, h) / 2f - dp(2f)
        val cx = w / 2f
        val cy = h / 2f

        val save = canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Fixed water level at ~55% down the circle.
        val level = (cy - radius) + 2f * radius * 0.55f
        val amp = dp(8f)
        val waveLen = w

        drawWave(canvas, level + dp(4f), amp * 0.7f, waveLen, phase + Math.PI.toFloat(), wavePaintBack)
        drawWave(canvas, level, amp, waveLen, phase, wavePaint)

        canvas.restoreToCount(save)
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    private fun drawWave(canvas: Canvas, level: Float, amp: Float, len: Float, ph: Float, paint: Paint) {
        wavePath.reset()
        val bottom = height.toFloat()
        wavePath.moveTo(0f, bottom)
        val k = (2 * Math.PI / len).toFloat()
        var x = 0f
        val step = dp(2f)
        while (x <= width) {
            val y = level + amp * sin((k * x + ph).toDouble()).toFloat()
            wavePath.lineTo(x, y)
            x += step
        }
        wavePath.lineTo(width.toFloat(), bottom)
        wavePath.close()
        canvas.drawPath(wavePath, paint)
    }
}
