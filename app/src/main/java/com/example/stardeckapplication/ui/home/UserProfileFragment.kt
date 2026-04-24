package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserProfileBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.SubscriptionPlanDao
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.ui.auth.ChangePasswordActivity
import com.example.stardeckapplication.ui.auth.LoginActivity
import com.example.stardeckapplication.ui.profile.AchievementsActivity
import com.example.stardeckapplication.ui.profile.LeaderboardActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.ui.profile.ReportIssueActivity
import com.example.stardeckapplication.util.AchievementSummaryHelper
import com.example.stardeckapplication.util.SessionManager
import com.example.stardeckapplication.util.ThemeManager
import com.example.stardeckapplication.util.ThemePrefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UserProfileFragment : Fragment(R.layout.fragment_user_profile) {

    private var _b: FragmentUserProfileBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val userDao by lazy { UserDao(dbHelper) }
    private val subscriptionDao by lazy { SubscriptionPlanDao(dbHelper) }
    private val achievementSummary by lazy { AchievementSummaryHelper(dbHelper) }

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
        b.btnLeaderboard.setOnClickListener {
            startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
        }

        b.btnSettings.setOnClickListener {
            val currentUser = session.load() ?: return@setOnClickListener
            startActivity(
                Intent(requireContext(), ChangePasswordActivity::class.java).apply {
                    putExtra(ChangePasswordActivity.EXTRA_SESSION, currentUser)
                    putExtra(ChangePasswordActivity.EXTRA_FORCE_MODE, false)
                }
            )
        }

        // "Appearance" is now hardcoded in XML — no .text setter needed
        b.btnNotifications.setOnClickListener { showAppearanceDialog() }

        b.btnPrivacyTerms.setOnClickListener { showPrivacyTermsDialog() }

        // ── Help / Report Issue ── now opens the real ReportIssueActivity
        b.btnHelp.setOnClickListener {
            startActivity(Intent(requireContext(), ReportIssueActivity::class.java))
        }

        b.btnFriends.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.stardeckapplication.ui.profile.FriendsActivity::class.java))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun showAppearanceDialog() {
        var selected = if (ThemePrefs.getThemeMode(requireContext()) == ThemePrefs.LIGHT) 1 else 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Appearance")
            .setSingleChoiceItems(arrayOf("Dark", "Light"), selected) { _, which ->
                selected = which
            }
            .setPositiveButton("Apply") { _, _ ->
                val mode = if (selected == 1) ThemePrefs.LIGHT else ThemePrefs.DARK
                ThemeManager.saveAndApplyTheme(requireContext(), mode)
                requireActivity().recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivacyTermsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Privacy & Terms")
            .setItems(arrayOf("Privacy Policy", "Terms")) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(
                            requireContext(),
                            com.example.stardeckapplication.ui.legal.PrivacyPolicyActivity::class.java
                        )
                    )
                    1 -> startActivity(
                        Intent(
                            requireContext(),
                            com.example.stardeckapplication.ui.legal.TermsActivity::class.java
                        )
                    )
                }
            }
            .show()
    }

    private fun bindProfile() {
        val me = session.load() ?: return
        b.tvName.text = me.name
        b.tvEmail.text = me.email

        // Set initials avatar
        b.tvInitials.text = me.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val currentPlan = subscriptionDao.getCurrentPlanForUser(me.id)
        val isPremium = currentPlan != null ||
                runCatching { userDao.isUserPremium(me.id) }.getOrDefault(false)
        val summary = achievementSummary.getSummary(me.id)

        b.tvPlanValue.text = when {
            currentPlan != null -> currentPlan.planName
            isPremium           -> "Premium Plan"
            else                -> "Free Plan"
        }

        val basePlanNote = when {
            currentPlan != null -> {
                buildString {
                    append("Billing: ${currentPlan.billingLabel} • ${currentPlan.priceText}")
                    if (currentPlan.expiresAt != null) {
                        append("\nSubscription is currently active.")
                    }
                }
            }
            isPremium -> "Premium access is currently active."
            else      -> "Upgrade to unlock premium decks and future AI tools."
        }

        b.tvPlanNote.text = if (summary.hasAny) {
            val nextLine = if (!summary.nextTitle.isNullOrBlank() &&
                !summary.nextProgressText.isNullOrBlank()
            ) {
                "\nAchievements: ${summary.unlockedCount}/${summary.totalCount} unlocked" +
                        " • Next: ${summary.nextTitle} (${summary.nextProgressText})"
            } else {
                "\nAchievements: ${summary.unlockedCount}/${summary.totalCount} unlocked"
            }
            basePlanNote + nextLine
        } else {
            basePlanNote
        }

        // Update achievement percent label
        val pct = if (summary.totalCount > 0)
            (summary.unlockedCount * 100 / summary.totalCount) else 0
        b.tvAchievementPercent.text = "$pct%"

        b.tvAchievementSummaryValue.text = "${summary.unlockedCount} / ${summary.totalCount} unlocked"
        b.progressAchievementSummary.progress = summary.unlockedCount

        // Update the Achievements row label inside its LinearLayout
        b.btnAchievements
            .findViewById<TextView>(
                b.btnAchievements.getChildAt(0).id.takeIf { it != View.NO_ID }
                    ?: run {
                        // fallback: find first TextView in the row
                        return@run View.NO_ID
                    }
            )

        // Simpler: find the first TextView child of btnAchievements directly
        val achievementsLabel = b.btnAchievements.getChildAt(0) as? TextView
        achievementsLabel?.text = if (summary.hasAny) {
            "Achievements • ${summary.unlockedCount}/${summary.totalCount}"
        } else {
            "Achievements"
        }
    }

    private fun showComingSoon(title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("This framework is ready. We will connect the real logic step by step.")
            .setPositiveButton("OK", null)
            .show()
    }
}
