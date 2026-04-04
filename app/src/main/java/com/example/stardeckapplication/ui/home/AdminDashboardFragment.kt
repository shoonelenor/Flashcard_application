package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminDashboardBinding
import com.example.stardeckapplication.databinding.ItemDashboardBreakdownRowBinding
import com.example.stardeckapplication.db.AdminDashboardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _b: FragmentAdminDashboardBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val dashboardDao by lazy { AdminDashboardDao(dbHelper) }
    private val userDao by lazy { UserDao(dbHelper) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _b = FragmentAdminDashboardBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            requireActivity().finish()
            return
        }

        val last: Long? = userDao.getLastLoginAt(me.id)
        val line = if (last == null) "First login" else "Last login: ${formatTime(last)}"
        b.tvSubtitle.text = "Welcome, ${me.name}\n$line"

        b.btnOpenMasterData.setOnClickListener {
            (activity as? AdminHomeActivity)?.openTab(R.id.admin_nav_master_data)
        }
        b.btnOpenReports.setOnClickListener {
            (activity as? AdminHomeActivity)?.openTab(R.id.admin_nav_reports)
        }
        b.btnOpenTickets.setOnClickListener {
            (activity as? AdminHomeActivity)?.openTab(R.id.admin_nav_ticket)
        }

        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        val snapshot = dashboardDao.getSummarySnapshot()

        b.tvUsersValue.text = snapshot.totalUsers.toString()
        b.tvDisabledValue.text = snapshot.disabledUsers.toString()
        b.tvPremiumValue.text = snapshot.premiumUsers.toString()
        b.tvDecksValue.text = snapshot.totalDecks.toString()
        b.tvHiddenValue.text = snapshot.hiddenDecks.toString()
        b.tvCardsValue.text = snapshot.totalCards.toString()
        b.tvStudySessionsValue.text = snapshot.totalStudySessions.toString()
        b.tvReportsValue.text = snapshot.openReports.toString()
        b.tvActiveMonthValue.text = snapshot.activeThisMonth.toString()
        b.tvInactiveMonthValue.text = snapshot.inactiveThisMonth.toString()
        b.tvAchievementsUnlockedValue.text = snapshot.unlockedAchievements.toString()
        b.tvConfiguredAchievementsValue.text = snapshot.configuredAchievements.toString()

        renderBreakdown(
            container = b.containerCategories,
            rows = dashboardDao.getTopCategories(),
            emptyLabel = "No category data yet"
        )
        renderBreakdown(
            container = b.containerLanguages,
            rows = dashboardDao.getTopLanguages(),
            emptyLabel = "No language data yet"
        )
        renderBreakdown(
            container = b.containerSubjects,
            rows = dashboardDao.getTopSubjects(),
            emptyLabel = "No subject data yet"
        )

        val topCategory = dashboardDao.getTopCategories(1).firstOrNull()?.label ?: "No category data"
        val topLanguage = dashboardDao.getTopLanguages(1).firstOrNull()?.label ?: "No language data"
        val topSubject = dashboardDao.getTopSubjects(1).firstOrNull()?.label ?: "No subject data"

        b.tvInsights.text = buildString {
            append("This month: ${snapshot.activeThisMonth} active users, ${snapshot.inactiveThisMonth} inactive users.\n")
            append("Content focus: $topCategory / $topSubject / $topLanguage.\n")
            append("Moderation: ${snapshot.openReports} open report(s).\n")
            append("Engagement: ${snapshot.unlockedAchievements} achievement unlock(s) across ${snapshot.configuredAchievements} configured rule(s).")
        }
    }

    private fun renderBreakdown(
        container: ViewGroup,
        rows: List<AdminDashboardDao.BreakdownRow>,
        emptyLabel: String
    ) {
        container.removeAllViews()

        val displayRows = if (rows.isEmpty()) {
            listOf(AdminDashboardDao.BreakdownRow(emptyLabel, 0))
        } else rows

        val max = displayRows.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        val inflater = LayoutInflater.from(container.context)

        displayRows.forEach { row ->
            val item = ItemDashboardBreakdownRowBinding.inflate(inflater, container, false)
            item.tvLabel.text = row.label
            item.tvValue.text = row.count.toString()
            item.progress.max = max
            item.progress.progress = row.count
            container.addView(item.root)
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}