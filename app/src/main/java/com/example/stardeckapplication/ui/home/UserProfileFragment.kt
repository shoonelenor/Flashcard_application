package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserProfileBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.auth.LoginActivity
import com.example.stardeckapplication.ui.profile.AchievementsActivity
import com.example.stardeckapplication.ui.profile.LeaderboardActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UserProfileFragment : Fragment(R.layout.fragment_user_profile) {

    private var _b: FragmentUserProfileBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserProfileBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.btnPremium.setOnClickListener {
            startActivity(Intent(requireContext(), PremiumDemoActivity::class.java))
        }

        b.btnAchievements.setOnClickListener {
            startActivity(Intent(requireContext(), AchievementsActivity::class.java))
        }

        b.btnSettings.setOnClickListener { showComingSoon("Change Password") }
        b.btnNotifications.setOnClickListener { showComingSoon("Notification Settings") }
        b.btnPrivacyTerms.setOnClickListener { showComingSoon("Privacy & Terms") }
        b.btnHelp.setOnClickListener { showComingSoon("Help / Report Issue") }
        b.btnFriends.setOnClickListener { showComingSoon("Friends") }

        // Real leaderboard now
        b.btnLeaderboard.setOnClickListener {
            startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
        }

        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout?")
                .setMessage("You will need to log in again to access your account.")
                .setPositiveButton("Logout") { _, _ ->
                    session.clear()
                    startActivity(
                        Intent(requireContext(), LoginActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        bindProfile()
    }

    private fun bindProfile() {
        val me = session.load() ?: return

        b.tvName.text = me.name
        b.tvEmail.text = me.email

        val isPremium = runCatching { db.isUserPremium(me.id) }.getOrDefault(false)

        b.tvPlanValue.text = if (isPremium) "Premium Plan" else "Free Plan"
        b.tvPlanNote.text = if (isPremium) {
            "Premium access is currently active."
        } else {
            "Upgrade to unlock premium decks and future AI tools."
        }
    }

    private fun showComingSoon(title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("This framework is ready. We will connect the real logic step by step.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
