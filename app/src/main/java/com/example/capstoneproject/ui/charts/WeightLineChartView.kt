package com.example.capstoneproject.ui.charts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.capstoneproject.R

/** Weight trend line over time (Canvas, no dependency). Points are (date label, kg), oldest first. */
class WeightLineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Point(val label: String, val kg: Double)

    private var points: List<Point> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.colorPrimary)
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.colorPrimary) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDE3E0"); strokeWidth = 1f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }

    fun setData(points: List<Point>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        if (points.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Log a weigh-in to start your trend", width / 2f, height / 2f, textPaint)
            return
        }
        val left = 40 * d
        val right = width - 12 * d
        val top = 12 * d
        val bottom = height - 22 * d
        var minKg = points.minOf { it.kg }
        var maxKg = points.maxOf { it.kg }
        if (maxKg - minKg < 2.0) { minKg -= 1.0; maxKg += 1.0 }   // keep a flat line readable
        val pad = (maxKg - minKg) * 0.15
        minKg -= pad; maxKg += pad
        fun y(kg: Double) = (bottom - ((kg - minKg) / (maxKg - minKg) * (bottom - top))).toFloat()

        // 3 horizontal gridlines with kg labels
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..2) {
            val kg = minKg + (maxKg - minKg) * i / 2
            val yy = y(kg)
            canvas.drawLine(left, yy, right, yy, gridPaint)
            canvas.drawText(String.format("%.1f", kg), left - 6 * d, yy + 4 * d, textPaint)
        }

        val stepX = if (points.size > 1) (right - left) / (points.size - 1) else 0f
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = left + stepX * i
            val yy = y(p.kg)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        canvas.drawPath(path, linePaint)
        textPaint.textAlign = Paint.Align.CENTER
        points.forEachIndexed { i, p ->
            val x = left + stepX * i
            canvas.drawCircle(x, y(p.kg), 4 * d, dotPaint)
            // label first, last and every ~4th point so they don't collide
            if (i == 0 || i == points.size - 1 || (points.size <= 8) || i % 4 == 0) {
                canvas.drawText(p.label, x, height - 6 * d, textPaint)
            }
        }
    }
}
