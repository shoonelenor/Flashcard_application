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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserHomeFragment : Fragment(R.layout.fragment_user_home) {

    private var _b: FragmentUserHomeBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserHomeBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        val last = db.getLastLoginAt(me.id)
        val line = if (last == null) "First login" else "Last login: ${formatTime(last)}"
        b.tvSubtitle.text = "Welcome, ${me.name}\n$line"

        b.btnContinue.setOnClickListener { openContinueDeck() }
        b.cardRecent.setOnClickListener { openContinueDeck() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openContinueDeck() {
        val me = session.load() ?: return
        val recent = db.getRecentlyStudiedDeck(me.id)
        val most = db.getMostStudiedDeck(me.id)
        val deckId = recent?.deckId ?: most?.deckId
        if (deckId != null) {
            startActivity(Intent(requireContext(), StudyActivity::class.java).putExtra(StudyActivity.EXTRA_DECK_ID, deckId))
        }
    }

    private fun refresh() {
        val me = session.load() ?: return

        // ✅ New: Progress metrics
        val todayCount = db.getTodayStudyCount(me.id)
        val streakDays = db.getStudyStreakDays(me.id)

        b.tvTodayValue.text = "$todayCount card(s) reviewed"
        b.tvStreakValue.text = if (streakDays == 1) "1 day" else "$streakDays days"

        // Existing insights
        val recent = db.getRecentlyStudiedDeck(me.id)
        val most = db.getMostStudiedDeck(me.id)

        if (recent == null) {
            b.tvRecentDeck.text = "No study history yet"
            b.tvRecentMeta.text = "Start studying to see insights."
            b.tvRecentHint.visibility = View.GONE
        } else {
            b.tvRecentDeck.text = recent.title
            b.tvRecentMeta.text = "Last studied on ${formatTime(recent.lastStudiedAt)}"
            b.tvRecentHint.visibility = View.VISIBLE
        }

        if (most == null) {
            b.tvMostDeck.text = "—"
            b.tvMostMeta.text = ""
        } else {
            b.tvMostDeck.text = most.title
            b.tvMostMeta.text = "${most.studyCount} ratings recorded"
        }

        val canContinue = (recent != null || most != null)
        b.btnContinue.isEnabled = canContinue
        b.btnContinue.alpha = if (canContinue) 1f else 0.5f
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