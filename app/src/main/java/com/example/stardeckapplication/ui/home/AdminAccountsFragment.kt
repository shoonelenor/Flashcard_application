package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminAccountsBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.ui.admin.ManageAccountsActivity
import com.example.stardeckapplication.ui.admin.ManageAchievementsActivity
import com.example.stardeckapplication.ui.admin.ManageCategoriesActivity
import com.example.stardeckapplication.ui.admin.ManageContentSetupActivity
import com.example.stardeckapplication.ui.admin.ManageLanguagesActivity
import com.example.stardeckapplication.ui.admin.ManageReportReasonsActivity
import com.example.stardeckapplication.ui.admin.ManageSubjectsActivity
import com.example.stardeckapplication.ui.admin.ManageSubscriptionPlansActivity
import com.example.stardeckapplication.util.SessionManager

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

        b.btnUser.setOnClickListener {
            startActivity(Intent(requireContext(), ManageAccountsActivity::class.java))
        }

        b.btnContent.setOnClickListener {
            startActivity(Intent(requireContext(), ManageContentSetupActivity::class.java))
        }

        b.btnCategory.setOnClickListener {
            startActivity(Intent(requireContext(), ManageCategoriesActivity::class.java))
        }

        b.btnSubject.setOnClickListener {
            startActivity(Intent(requireContext(), ManageSubjectsActivity::class.java))
        }

        b.btnLanguage.setOnClickListener {
            startActivity(Intent(requireContext(), ManageLanguagesActivity::class.java))
        }

        b.btnReportReason.setOnClickListener {
            startActivity(Intent(requireContext(), ManageReportReasonsActivity::class.java))
        }

        b.btnAchievement.setOnClickListener {
            startActivity(Intent(requireContext(), ManageAchievementsActivity::class.java))
        }

        b.btnSubscription.setOnClickListener {
            startActivity(Intent(requireContext(), ManageSubscriptionPlansActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}