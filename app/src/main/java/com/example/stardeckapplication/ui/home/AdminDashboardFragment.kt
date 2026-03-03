package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminDashboardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.admin.ManageAccountsActivity
import com.example.stardeckapplication.util.SessionManager

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

        b.btnManageAccounts.setOnClickListener {
            startActivity(Intent(requireContext(), ManageAccountsActivity::class.java))
        }

        refreshStats()
    }

    private fun formatTime(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ms))
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        // Read-only queries = safe
        b.tvUsersValue.text = db.adminCountAllUsers().toString()
        b.tvDisabledValue.text = db.adminCountDisabledUsers().toString()
        b.tvPremiumValue.text = db.adminCountPremiumUsers().toString()
        b.tvDecksValue.text = db.adminCountAllDecks().toString()
        b.tvHiddenValue.text = db.adminCountHiddenDecks().toString()
        b.tvReportsValue.text = db.adminCountOpenReports().toString()
        b.tvActiveMonthValue.text = db.countMonthlyActiveUsers().toString()
        b.tvInactiveMonthValue.text = db.countMonthlyInactiveUsers().toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}