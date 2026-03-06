package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.study.StudyActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class UserHomeFragment : Fragment(R.layout.fragment_user_home) {

    private var _b: FragmentUserHomeBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    private var canContinueStudy = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserHomeBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.tvSubtitle.text = "Welcome back, ${me.name}. Keep your learning progress going today."

        b.btnContinue.setOnClickListener {
            if (canContinueStudy) {
                openContinueDeck()
            } else {
                (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            }
        }

        b.cardRecent.setOnClickListener {
            if (canContinueStudy) {
                openContinueDeck()
            }
        }

        b.btnNewDeck.setOnClickListener {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            Snackbar.make(b.root, "Open Library and tap + to create a deck.", Snackbar.LENGTH_SHORT).show()
        }

        b.btnImportHome.setOnClickListener {
            Snackbar.make(b.root, "Import framework is ready. Real import logic comes next.", Snackbar.LENGTH_SHORT).show()
        }

        b.btnAiHome.setOnClickListener {
            Snackbar.make(b.root, "AI framework is ready. Real AI logic comes later.", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val me = session.load() ?: return

        val todayCount = db.getTodayStudyCount(me.id)
        val streakDays = db.getStudyStreakDays(me.id)
        val recent = db.getRecentlyStudiedDeck(me.id)
        val most = db.getMostStudiedDeck(me.id)

        b.tvTodayValue.text = if (todayCount == 1) "1 card reviewed" else "$todayCount cards reviewed"
        b.tvStreakValue.text = if (streakDays == 1) "1 day" else "$streakDays days"

        val goal = 10
        b.progressDailyGoal.max = goal
        b.progressDailyGoal.progress = min(todayCount, goal)
        b.tvDailyGoalValue.text = "${min(todayCount, goal)} / $goal cards"

        b.tvDueValue.text = "Due queue framework ready"

        if (recent == null) {
            b.tvRecentDeck.text = "No recent study session"
            b.tvRecentMeta.text = "Start with a deck from your library."
            b.tvRecentHint.text = "Your last study session will appear here."
        } else {
            b.tvRecentDeck.text = recent.title
            b.tvRecentMeta.text = "Last studied ${formatTime(recent.lastStudiedAt)}"
            b.tvRecentHint.text = "Tap here or the button above to continue."
        }

        if (most == null) {
            b.tvMostDeck.text = "No favorite deck yet"
            b.tvMostMeta.text = "Your most-studied deck will appear here."
        } else {
            b.tvMostDeck.text = most.title
            b.tvMostMeta.text = "${most.studyCount} ratings recorded"
        }

        canContinueStudy = (recent != null || most != null)
        b.btnContinue.text = if (canContinueStudy) "Continue Study" else "Open Library"
    }

    private fun openContinueDeck() {
        val me = session.load() ?: return
        val recent = db.getRecentlyStudiedDeck(me.id)
        val most = db.getMostStudiedDeck(me.id)
        val deckId = recent?.deckId ?: most?.deckId ?: return

        startActivity(
            Intent(requireContext(), StudyActivity::class.java)
                .putExtra(StudyActivity.EXTRA_DECK_ID, deckId)
        )
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