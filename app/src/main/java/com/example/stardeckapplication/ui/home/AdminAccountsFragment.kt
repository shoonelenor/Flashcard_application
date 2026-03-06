package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminAccountsBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.ui.admin.ManageContentSetupActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AdminAccountsFragment : Fragment(R.layout.fragment_admin_accounts) {

    private var _b: FragmentAdminAccountsBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentAdminAccountsBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            requireActivity().finish()
            return
        }

        b.btnUser.setOnClickListener { showComingSoon("User Management") }
        b.btnContent.setOnClickListener {
            startActivity(Intent(requireContext(), ManageContentSetupActivity::class.java))
        }
        b.btnCategory.setOnClickListener { showComingSoon("Category Setup") }
        b.btnSubject.setOnClickListener { showComingSoon("Subject Setup") }
        b.btnLanguage.setOnClickListener { showComingSoon("Language Setup") }
        b.btnSubscription.setOnClickListener { showComingSoon("Subscription Plan Setup") }
        b.btnReportReason.setOnClickListener { showComingSoon("Report Reason Setup") }
        b.btnAchievement.setOnClickListener { showComingSoon("Achievement Setup") }
    }

    private fun showComingSoon(title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(
                "This framework tab is ready.\n\n" +
                        "We will connect the real master-data logic safely later."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}