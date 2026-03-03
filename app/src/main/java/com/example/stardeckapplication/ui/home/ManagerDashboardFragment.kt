package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentManagerDashboardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerDashboardFragment : Fragment(R.layout.fragment_manager_dashboard) {

    private var _b: FragmentManagerDashboardBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentManagerDashboardBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            requireActivity().finish()
            return
        }

        val last = db.getLastLoginAt(me.id)
        val line = if (last == null) "First login" else "Last login: ${formatTime(last)}"
        b.tvSubtitle.text = "Welcome, ${me.name}\n$line"

        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        b.tvOpenReports.text = db.adminCountOpenReports().toString()
        b.tvHiddenDecks.text = db.adminCountHiddenDecks().toString()
        b.tvTotalDecks.text = db.adminCountAllDecks().toString()

        // if you added the MAU card to manager dashboard layout
        if (runCatching { b.tvActiveMonthValue }.isSuccess) {
            b.tvActiveMonthValue.text = db.countMonthlyActiveUsers().toString()
        }
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