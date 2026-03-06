package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminDashboardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private var _b: FragmentAdminDashboardBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentAdminDashboardBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            requireActivity().finish()
            return
        }

        val last = db.getLastLoginAt(me.id)
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
        b.tvUsersValue.text = db.adminCountAllUsers().toString()
        b.tvDisabledValue.text = db.adminCountDisabledUsers().toString()
        b.tvPremiumValue.text = db.adminCountPremiumUsers().toString()
        b.tvDecksValue.text = db.adminCountAllDecks().toString()
        b.tvHiddenValue.text = db.adminCountHiddenDecks().toString()
        b.tvReportsValue.text = db.adminCountOpenReports().toString()
        b.tvActiveMonthValue.text = db.countMonthlyActiveUsers().toString()
        b.tvInactiveMonthValue.text = db.countMonthlyInactiveUsers().toString()
    }

    private fun formatTime(ms: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}