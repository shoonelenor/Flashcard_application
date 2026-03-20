package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentManagerDashboardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerDashboardFragment : Fragment(R.layout.fragment_manager_dashboard) {

    private var _b: FragmentManagerDashboardBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _b = FragmentManagerDashboardBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            requireActivity().finish()
            return
        }

        bindHeader(me.name, db.getLastLoginAt(me.id))
        setupActions()
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun bindHeader(name: String, lastLoginAt: Long?) {
        val subtitleLine = if (lastLoginAt == null) {
            "First login"
        } else {
            "Last login: ${formatTime(lastLoginAt)}"
        }

        b.tvSubtitle.text = "Welcome, $name\n$subtitleLine"
    }

    private fun setupActions() {
        b.btnGoReports.setOnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
            bottomNav?.selectedItemId = R.id.manager_nav_reports
        }
    }

    private fun refreshStats() {
        b.tvOpenReports.text = db.adminCountOpenReports().toString()
        b.tvHiddenDecks.text = db.adminCountHiddenDecks().toString()
        b.tvTotalDecks.text = db.adminCountAllDecks().toString()
        b.tvActiveMonthValue.text = db.countMonthlyActiveUsers().toString()
    }

    private fun formatTime(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}