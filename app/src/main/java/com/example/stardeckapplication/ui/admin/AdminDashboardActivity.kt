package com.example.stardeckapplication.ui.admin

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityAdminDashboardBinding
import com.example.stardeckapplication.db.AdminDashboardDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminDashboardBinding
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val dao      by lazy { AdminDashboardDao(dbHelper) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Dashboard"

        loadData()
    }

    private fun loadData() {
        val snap       = dao.getSummarySnapshot()
        val categories = dao.getTopCategories(5)
        val languages  = dao.getTopLanguages(5)
        val subjects   = dao.getTopSubjects(5)

        // ── KPI cards ────────────────────────────────────────────────────────
        b.tvTotalUsers.text          = snap.totalUsers.toString()
        b.tvPremiumUsers.text        = snap.premiumUsers.toString()
        b.tvDisabledUsers.text       = snap.disabledUsers.toString()
        b.tvTotalDecks.text          = snap.totalDecks.toString()
        b.tvHiddenDecks.text         = snap.hiddenDecks.toString()
        b.tvTotalCards.text          = snap.totalCards.toString()
        b.tvStudySessions.text       = snap.totalStudySessions.toString()
        b.tvOpenReports.text         = snap.openReports.toString()
        b.tvActiveThisMonth.text     = snap.activeThisMonth.toString()
        b.tvInactiveThisMonth.text   = snap.inactiveThisMonth.toString()
        b.tvAchievementsUnlocked.text = snap.unlockedAchievements.toString()

        // ── User status pie chart ─────────────────────────────────────────────
        val activeUsers   = (snap.totalUsers - snap.disabledUsers).coerceAtLeast(0)
        val userEntries = listOf(
            PieEntry(activeUsers.toFloat(),  "Active"),
            PieEntry(snap.premiumUsers.toFloat(), "Premium"),
            PieEntry(snap.disabledUsers.toFloat(), "Disabled")
        ).filter { it.value > 0f }

        if (userEntries.isNotEmpty()) {
            val userDataSet = PieDataSet(userEntries, "").apply {
                colors = listOf(
                    Color.parseColor("#01696f"),
                    Color.parseColor("#4f98a3"),
                    Color.parseColor("#a12c7b")
                )
                valueTextColor  = Color.WHITE
                valueTextSize   = 12f
                valueFormatter  = PercentFormatter(b.pieUsers)
                sliceSpace      = 2f
            }
            b.pieUsers.apply {
                data = PieData(userDataSet)
                description.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius        = 52f
                transparentCircleRadius = 57f
                setHoleColor(Color.TRANSPARENT)
                setUsePercentValues(true)
                setEntryLabelTextSize(11f)
                setEntryLabelColor(Color.WHITE)
                legend.apply {
                    isEnabled        = true
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    orientation      = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    textColor        = resolveTextColor()
                    textSize         = 11f
                }
                animateY(900, Easing.EaseInOutQuad)
                invalidate()
            }
        }

        // ── Deck visibility pie chart ─────────────────────────────────────────
        val visibleDecks = (snap.totalDecks - snap.hiddenDecks).coerceAtLeast(0)
        val deckEntries = listOf(
            PieEntry(visibleDecks.toFloat(),    "Visible"),
            PieEntry(snap.hiddenDecks.toFloat(), "Hidden")
        ).filter { it.value > 0f }

        if (deckEntries.isNotEmpty()) {
            val deckDataSet = PieDataSet(deckEntries, "").apply {
                colors = listOf(
                    Color.parseColor("#437a22"),
                    Color.parseColor("#a13544")
                )
                valueTextColor  = Color.WHITE
                valueTextSize   = 12f
                valueFormatter  = PercentFormatter(b.pieDecks)
                sliceSpace      = 2f
            }
            b.pieDecks.apply {
                data = PieData(deckDataSet)
                description.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius        = 52f
                transparentCircleRadius = 57f
                setHoleColor(Color.TRANSPARENT)
                setUsePercentValues(true)
                setEntryLabelTextSize(11f)
                setEntryLabelColor(Color.WHITE)
                legend.apply {
                    isEnabled           = true
                    verticalAlignment   = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    orientation         = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    textColor           = resolveTextColor()
                    textSize            = 11f
                }
                animateY(900, Easing.EaseInOutQuad)
                invalidate()
            }
        }

        // ── Monthly activity pie chart ────────────────────────────────────────
        val activityEntries = listOf(
            PieEntry(snap.activeThisMonth.toFloat(),   "Active"),
            PieEntry(snap.inactiveThisMonth.toFloat(), "Inactive")
        ).filter { it.value > 0f }

        if (activityEntries.isNotEmpty()) {
            val activityDataSet = PieDataSet(activityEntries, "").apply {
                colors = listOf(
                    Color.parseColor("#006494"),
                    Color.parseColor("#da7101")
                )
                valueTextColor  = Color.WHITE
                valueTextSize   = 12f
                valueFormatter  = PercentFormatter(b.pieActivity)
                sliceSpace      = 2f
            }
            b.pieActivity.apply {
                data = PieData(activityDataSet)
                description.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius        = 52f
                transparentCircleRadius = 57f
                setHoleColor(Color.TRANSPARENT)
                setUsePercentValues(true)
                setEntryLabelTextSize(11f)
                setEntryLabelColor(Color.WHITE)
                legend.apply {
                    isEnabled           = true
                    verticalAlignment   = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    orientation         = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    textColor           = resolveTextColor()
                    textSize            = 11f
                }
                animateY(900, Easing.EaseInOutQuad)
                invalidate()
            }
        }

        // ── Top categories bar chart ──────────────────────────────────────────
        if (categories.isNotEmpty()) {
            val barEntries = categories.mapIndexed { i, row -> BarEntry(i.toFloat(), row.count.toFloat()) }
            val labels     = categories.map { it.label }
            val catDataSet = BarDataSet(barEntries, "Decks per Category").apply {
                colors = listOf(
                    Color.parseColor("#01696f"),
                    Color.parseColor("#4f98a3"),
                    Color.parseColor("#437a22"),
                    Color.parseColor("#6daa45"),
                    Color.parseColor("#006494")
                )
                valueTextColor = resolveTextColor()
                valueTextSize  = 11f
            }
            b.barCategories.apply {
                data = BarData(catDataSet).apply { barWidth = 0.6f }
                description.isEnabled = false
                setFitBars(true)
                xAxis.apply {
                    valueFormatter      = IndexAxisValueFormatter(labels)
                    granularity         = 1f
                    setDrawGridLines(false)
                    textColor           = resolveTextColor()
                    textSize            = 10f
                    labelRotationAngle  = -25f
                }
                axisLeft.apply {
                    granularity = 1f
                    textColor   = resolveTextColor()
                    setDrawGridLines(true)
                }
                axisRight.isEnabled = false
                legend.isEnabled    = false
                animateY(900, Easing.EaseInOutQuad)
                invalidate()
            }
        }

        // ── Top languages bar chart ───────────────────────────────────────────
        if (languages.isNotEmpty()) {
            val barEntries = languages.mapIndexed { i, row -> BarEntry(i.toFloat(), row.count.toFloat()) }
            val labels     = languages.map { it.label }
            val langDataSet = BarDataSet(barEntries, "Decks per Language").apply {
                colors = listOf(
                    Color.parseColor("#7a39bb"),
                    Color.parseColor("#a86fdf"),
                    Color.parseColor("#d163a7"),
                    Color.parseColor("#a12c7b"),
                    Color.parseColor("#4f98a3")
                )
                valueTextColor = resolveTextColor()
                valueTextSize  = 11f
            }
            b.barLanguages.apply {
                data = BarData(langDataSet).apply { barWidth = 0.6f }
                description.isEnabled = false
                setFitBars(true)
                xAxis.apply {
                    valueFormatter      = IndexAxisValueFormatter(labels)
                    granularity         = 1f
                    setDrawGridLines(false)
                    textColor           = resolveTextColor()
                    textSize            = 10f
                    labelRotationAngle  = -25f
                }
                axisLeft.apply {
                    granularity = 1f
                    textColor   = resolveTextColor()
                    setDrawGridLines(true)
                }
                axisRight.isEnabled = false
                legend.isEnabled    = false
                animateY(900, Easing.EaseInOutQuad)
                invalidate()
            }
        }

        // ── Top subjects bar chart ────────────────────────────────────────────
        if (subjects.isNotEmpty()) {
            val barEntries = subjects.mapIndexed { i, row -> BarEntry(i.toFloat(), row.count.toFloat()) }
            val labels     = subjects.map { it.label }
            val subjDataSet = BarDataSet(barEntries, "Decks per Subject").apply {
                colors = listOf(
                    Color.parseColor("#d19900"),
                    Color.parseColor("#e8af34"),
                    Color.parseColor("#da7101"),
                    Color.parseColor("#fdab43"),
                    Color.parseColor("#437a22")
                )
                valueTextColor = resolveTextColor()
                valueTextSize  = 11f
            }
            b.barSubjects.apply {
                data = BarData(subjDataSet).apply { barWidth = 0.6f }
                description.isEnabled = false
                setFitBars(true)
                xAxis.apply {
                    valueFormatter      = IndexAxisValueFormatter(labels)
                    granularity         = 1f
                    setDrawGridLines(false)
                    textColor           = resolveTextColor()
                    textSize            = 10f
                    labelRotationAngle  = -25f
                }
                axisLeft.apply {
                    granularity = 1f
                    textColor   = resolveTextColor()
                    setDrawGridLines(true)
                }
                axisRight.isEnabled = false
                legend.isEnabled    = false
                animateY(900, Easing.EaseInOutQuad)
                invalidate()
            }
        }
    }

    private fun resolveTextColor(): Int {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val c  = ta.getColor(0, Color.BLACK)
        ta.recycle()
        return c
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
