package com.example.stardeckapplication.ui.stats

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StatsDao
import com.example.stardeckapplication.db.StatsDao.ManagerStats
import java.util.concurrent.Executors

class ManagerStatsFragment : Fragment() {

    private val executor = Executors.newSingleThreadExecutor()
    private val statsDao by lazy { StatsDao(StarDeckDbHelper(requireContext())) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_manager_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadStats(it) }
    }

    private fun loadStats(root: View) {
        executor.execute {
            val stats = runCatching { statsDao.getManagerStats() }.getOrNull() ?: return@execute
            activity?.runOnUiThread {
                if (isAdded) bindStats(root, stats)
            }
        }
    }

    private fun bindStats(v: View, s: ManagerStats) {
        v.findViewById<TextView>(R.id.tvMgrStatTotalDecks).text   = s.totalDecks.toString()
        v.findViewById<TextView>(R.id.tvMgrStatActiveDecks).text  = s.activeDecks.toString()
        v.findViewById<TextView>(R.id.tvMgrStatHiddenDecks).text  = s.hiddenDecks.toString()
        v.findViewById<TextView>(R.id.tvMgrStatPublicDecks).text  = s.publicDecks.toString()
        v.findViewById<TextView>(R.id.tvMgrStatPremiumDecks).text = s.premiumDecks.toString()
        v.findViewById<TextView>(R.id.tvMgrStatTotalCards).text   = s.totalCards.toString()
        v.findViewById<TextView>(R.id.tvMgrStatSessions).text     = s.totalSessions.toString()
        v.findViewById<TextView>(R.id.tvMgrStatKnown).text        = s.knownSessions.toString()
        v.findViewById<TextView>(R.id.tvMgrStatHard).text         = s.hardSessions.toString()
        v.findViewById<TextView>(R.id.tvMgrStatOpenRep).text      = s.openReports.toString()
        v.findViewById<TextView>(R.id.tvMgrStatResolvedRep).text  = s.resolvedReports.toString()

        drawBarChart(
            container   = v.findViewById(R.id.mgrBarChartSessions),
            data        = s.sessionsByDay,
            barColorRes = R.color.colorPrimary
        )
        drawHorizontalBars(
            container   = v.findViewById(R.id.mgrBarChartTopDecks),
            data        = s.topDecksByCards,
            barColorRes = R.color.teal_700
        )
    }

    private fun drawBarChart(
        container: LinearLayout,
        data: List<Pair<String, Int>>,
        barColorRes: Int
    ) {
        container.removeAllViews()
        if (data.isEmpty()) return
        val ctx = container.context
        val maxVal = data.maxOf { it.second }.coerceAtLeast(1)
        val dp = ctx.resources.displayMetrics.density
        val barWidth = (40 * dp).toInt()
        val maxHeight = (120 * dp).toInt()
        val barColor = try {
            ContextCompat.getColor(ctx, barColorRes)
        } catch (_: Exception) { Color.parseColor("#01696f") }

        for ((label, value) in data) {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).also { it.marginEnd = (8 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = value.toString()
                textSize = 10f
                setTextColor(Color.parseColor("#7a7974"))
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            })
            val barH = ((value.toFloat() / maxVal) * maxHeight).toInt().coerceAtLeast((4 * dp).toInt())
            col.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(barWidth, barH)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(barColor)
                    cornerRadius = 6 * dp
                }
            })
            col.addView(TextView(ctx).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#7a7974"))
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            container.addView(col)
        }
    }

    private fun drawHorizontalBars(
        container: LinearLayout,
        data: List<Pair<String, Int>>,
        barColorRes: Int
    ) {
        container.removeAllViews()
        if (data.isEmpty()) return
        val ctx = container.context
        val maxVal = data.maxOf { it.second }.coerceAtLeast(1)
        val dp = ctx.resources.displayMetrics.density
        val barColor = try {
            ContextCompat.getColor(ctx, barColorRes)
        } catch (_: Exception) { Color.parseColor("#01696f") }

        for ((label, value) in data) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (6 * dp).toInt() }
            }
            row.addView(TextView(ctx).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#28251d"))
                layoutParams = LinearLayout.LayoutParams((110 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val barContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val barW = ((value.toFloat() / maxVal) * (180 * dp)).toInt().coerceAtLeast((4 * dp).toInt())
            barContainer.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(barW, (14 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(barColor)
                    cornerRadius = 4 * dp
                }
            })
            barContainer.addView(TextView(ctx).apply {
                text = "  $value"
                textSize = 10f
                setTextColor(Color.parseColor("#7a7974"))
            })
            row.addView(barContainer)
            container.addView(row)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
