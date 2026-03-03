package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserProfileBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.ui.auth.LoginActivity
import com.example.stardeckapplication.ui.profile.AchievementsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UserProfileFragment : Fragment(R.layout.fragment_user_profile) {

    private var _b: FragmentUserProfileBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserProfileBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.tvName.text = me.name
        b.tvEmail.text = me.email

        // ✅ Achievements is now implemented
        b.btnAchievements.setOnClickListener {
            startActivity(Intent(requireContext(), AchievementsActivity::class.java))
        }

        // Keep others safe (no feature yet)
        b.btnPremium.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.stardeckapplication.ui.profile.PremiumDemoActivity::class.java))
        }
        b.btnSettings.setOnClickListener { comingSoon("Settings") }

        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout?")
                .setMessage("You will need to log in again to access your decks.")
                .setPositiveButton("Logout") { _, _ ->
                    session.clear()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun comingSoon(title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("This feature will be added in the next iteration.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}