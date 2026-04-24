package com.example.stardeckapplication.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.stardeckapplication.db.StatsDao
import kotlin.math.max

class StudyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<StatsDao.DayCount> = emptyList()

    // ── Colors matching your stardeck palette ──
    private val colorBarNormal  = 0xFF4F6BFF.toInt()   // stardeck_primary
    private val colorBarToday   = 0xFF3A8DFF.toInt()   // stardeck_bottom_nav_icon_active
    private val colorBarBg      = 0xFF1F2B4D.toInt()   // stardeck_field_stroke
    private val colorLabel      = 0xFF9CA7C7.toInt()   // stardeck_text_secondary
    private val colorCount      = 0xFFF5F7FF.toInt()   // stardeck_text_primary
    private val colorZeroLine   = 0xFF1F2B4D.toInt()

    private val paintBar   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBg    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = 28f
    }
    private val paintCount = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = 26f
    }
    private val paintLine  = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()

    fun setData(days: List<StatsDao.DayCount>) {
        data = days
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w        = width.toFloat()
        val h        = height.toFloat()
        val maxCount = max(1, data.maxOf { it.count })

        val labelH  = 44f   // bottom label area
        val countH  = 36f   // top count label area
        val padH    = 16f   // top padding
        val barAreaH = h - labelH - countH - padH

        val totalBars = data.size
        val barW      = (w / totalBars) * 0.55f
        val gap       = (w / totalBars) * 0.45f
        val slotW     = w / totalBars
        val radius    = 10f

        // Zero baseline
        paintLine.color = colorZeroLine
        paintLine.strokeWidth = 1.5f
        canvas.drawLine(0f, h - labelH, w, h - labelH, paintLine)

        for ((i, day) in data.withIndex()) {
            val cx      = slotW * i + slotW / 2f
            val barLeft = cx - barW / 2f
            val barRight= cx + barW / 2f

            // Background bar (empty track)
            paintBg.color = colorBarBg
            rect.set(barLeft, padH + countH, barRight, h - labelH)
            canvas.drawRoundRect(rect, radius, radius, paintBg)

            // Filled bar
            if (day.count > 0) {
                val filledH = (day.count.toFloat() / maxCount) * barAreaH
                val top     = h - labelH - filledH
                paintBar.color = if (day.isToday) colorBarToday else colorBarNormal

                rect.set(barLeft, top, barRight, h - labelH)
                canvas.drawRoundRect(rect, radius, radius, paintBar)
            }

            // Count label above bar
            paintCount.color = if (day.isToday) colorBarToday else colorLabel
            paintCount.isFakeBoldText = day.isToday
            val countText = if (day.count > 0) day.count.toString() else ""
            canvas.drawText(countText, cx, padH + countH - 6f, paintCount)

            // Day label below
            paintLabel.color = if (day.isToday) colorBarToday else colorLabel
            paintLabel.isFakeBoldText = day.isToday
            canvas.drawText(day.label, cx, h - 10f, paintLabel)
        }
    }
}