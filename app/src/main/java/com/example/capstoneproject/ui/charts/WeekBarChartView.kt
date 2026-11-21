package com.example.capstoneproject.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.capstoneproject.R
import kotlin.math.max

/**
 * Grouped bar chart: calories eaten vs burned per day for the last N days, with the daily
 * target as a dashed line. Drawn with Canvas so the app needs no chart dependency.
 */
class WeekBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Day(val label: String, val eaten: Int, val burned: Int)

    private var days: List<Day> = emptyList()
    private var target: Int? = null

    private val eatenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.colorPrimary) }
    private val burnedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.colorAccent) }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDE3E0"); strokeWidth = 1f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    fun setData(days: List<Day>, target: Int?) {
        this.days = days
        this.target = target
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val d = resources.displayMetrics.density
        val left = 8 * d
        val right = width - 8 * d
        val top = 12 * d
        val bottom = height - 22 * d          // room for day labels
        val maxVal = max(max(days.maxOf { it.eaten }, days.maxOf { it.burned }), target ?: 0).coerceAtLeast(500)
        val scale = (bottom - top) / maxVal.toFloat()

        // baseline
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        val groupW = (right - left) / days.size
        val barW = groupW * 0.32f
        days.forEachIndexed { i, day ->
            val cx = left + groupW * i + groupW / 2
            val eatenH = day.eaten * scale
            val burnedH = day.burned * scale
            canvas.drawRoundRect(cx - barW - 2 * d, bottom - eatenH, cx - 2 * d, bottom, 4 * d, 4 * d, eatenPaint)
            canvas.drawRoundRect(cx + 2 * d, bottom - burnedH, cx + barW + 2 * d, bottom, 4 * d, 4 * d, burnedPaint)
            canvas.drawText(day.label, cx, height - 6 * d, textPaint)
        }

        target?.let { t ->
            val y = bottom - t * scale
            canvas.drawLine(left, y, right, y, targetPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("target $t", right, y - 4 * d, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }
}
