package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminDashboardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StatsDao
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _b: FragmentAdminDashboardBinding? = null
    private val b get() = _b!!

    private val session  by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val stats    by lazy { StatsDao(dbHelper) }
    private val userDao  by lazy { UserDao(dbHelper) }  // ✅ getLastLoginAt, countMonthlyActiveUsers, countMonthlyInactiveUsers

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _b = FragmentAdminDashboardBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            requireActivity().finish()
            return
        }

        // ✅ userDao.getLastLoginAt returns Long? — no type mismatch
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
        b.tvUsersValue.text         = stats.adminCountAllUsers().toString()
        b.tvDisabledValue.text      = stats.adminCountDisabledUsers().toString()
        b.tvPremiumValue.text       = stats.adminCountPremiumUsers().toString()
        b.tvDecksValue.text         = stats.adminCountAllDecks().toString()
        b.tvHiddenValue.text        = stats.adminCountHiddenDecks().toString()
        b.tvReportsValue.text       = stats.adminCountOpenReports().toString()
        // ✅ moved to UserDao
        b.tvActiveMonthValue.text   = userDao.countMonthlyActiveUsers().toString()
        b.tvInactiveMonthValue.text = userDao.countMonthlyInactiveUsers().toString()
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}