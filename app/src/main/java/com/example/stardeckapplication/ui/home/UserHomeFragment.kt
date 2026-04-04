package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StatsDao
import com.example.stardeckapplication.db.StudyDao
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.ui.study.StudyActivity
import com.example.stardeckapplication.util.AchievementSummaryHelper
import com.example.stardeckapplication.util.AchievementSyncHelper
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
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val statsDao by lazy { StatsDao(dbHelper) }
    private val studyDao by lazy { StudyDao(dbHelper) }
    private val userDao by lazy { UserDao(dbHelper) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }
    private val achievementSummary by lazy { AchievementSummaryHelper(dbHelper) }

    // ─────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────

    private data class DueDeckCandidate(
        val deckId: Long,
        val title: String,
        val dueCount: Int,
        val isPremium: Boolean,
        val isRecent: Boolean
    )

    private enum class PrimaryAction { OPEN_LIBRARY, CONTINUE_STUDY, STUDY_DUE }

    private var canContinueStudy = false
    private var isPremiumUser = false
    private var recentDeckId: Long? = null
    private var recentDeckPremium = false
    private var mostStudiedDeckId: Long? = null
    private var mostStudiedDeckPremium = false
    private var bestDueDeckId: Long? = null
    private var bestDueDeckPremium = false
    private var primaryAction = PrimaryAction.OPEN_LIBRARY

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserHomeBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // ─────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        b.btnContinue.setOnClickListener { openPrimaryAction() }

        b.cardRecent.setOnClickListener {
            if (canContinueStudy) openContinueDeck()
            else Snackbar.make(b.root, "No recent study session yet.", Snackbar.LENGTH_SHORT).show()
        }

        b.cardDue.setOnClickListener { openBestDueDeck() }
        b.cardMost.setOnClickListener { openMostStudiedDeck() }

        b.btnNewDeck.setOnClickListener {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            Snackbar.make(b.root, "Open Library and tap + to create a deck.", Snackbar.LENGTH_SHORT).show()
        }

        b.btnImportHome.setOnClickListener {
            startActivity(Intent(requireContext(), ImportDeckActivity::class.java))
        }

        b.btnAiHome.setOnClickListener {
            startActivity(Intent(requireContext(), SmartGenerateActivity::class.java))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Data refresh
    // ─────────────────────────────────────────────────────────────

    private fun refresh() {
        val me = session.load() ?: return

        achievementSync.syncForUser(me.id)
        val summary = achievementSummary.getSummary(me.id)

        val todayCount = runCatching { statsDao.getTodayStudyCount(me.id) }.getOrDefault(0)
        val streakDays = runCatching { statsDao.getStudyStreakDays(me.id) }.getOrDefault(0)
        val recent     = runCatching { statsDao.getRecentlyStudiedDeck(me.id) }.getOrNull()
        val most       = runCatching { statsDao.getMostStudiedDeck(me.id) }.getOrNull()
        val dueCount   = runCatching { studyDao.getDueCountForUser(me.id) }.getOrDefault(0)

        val decks = runCatching { deckDao.getDecksForOwner(me.id) }
            .getOrDefault(emptyList())
            .filter { it.status == DbContract.DECK_ACTIVE }

        val deckById = decks.associateBy { it.id }

        isPremiumUser          = runCatching { userDao.isUserPremium(me.id) }.getOrDefault(false)
        recentDeckId           = recent?.deckId
        recentDeckPremium      = recent?.deckId?.let { deckById[it]?.isPremium } == true
        mostStudiedDeckId      = most?.deckId
        mostStudiedDeckPremium = most?.deckId?.let { deckById[it]?.isPremium } == true

        val dueCandidates = decks.mapNotNull { deck ->
            val count = runCatching { studyDao.getDueCountForDeck(me.id, deck.id) }.getOrDefault(0)
            if (count <= 0 || (deck.isPremium && !isPremiumUser)) return@mapNotNull null
            DueDeckCandidate(
                deckId    = deck.id,
                title     = deck.title,
                dueCount  = count,
                isPremium = deck.isPremium,
                isRecent  = recent?.deckId == deck.id
            )
        }

        val bestDue = dueCandidates
            .sortedWith(
                compareByDescending<DueDeckCandidate> { it.dueCount }
                    .thenByDescending { it.isRecent }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
            .firstOrNull()

        bestDueDeckId      = bestDue?.deckId
        bestDueDeckPremium = bestDue?.isPremium == true
        canContinueStudy   = recent != null || most != null

        bindUi(todayCount, streakDays, dueCount, bestDue, recent, most, summary, me.name)
    }

    // ─────────────────────────────────────────────────────────────
    // UI binding
    // ─────────────────────────────────────────────────────────────

    private fun bindUi(
        todayCount: Int,
        streakDays: Int,
        dueCount: Int,
        bestDue: DueDeckCandidate?,
        recent: StatsDao.RecentDeckRow?,
        most: StatsDao.MostStudiedDeckRow?,
        summary: AchievementSummaryHelper.Summary,
        userName: String
    ) {
        // Stats row
        b.tvTodayValue.text = if (todayCount == 1) "1 card reviewed" else "$todayCount cards reviewed"
        b.tvStreakValue.text = if (streakDays == 1) "1 day" else "$streakDays days"

        // Daily goal progress
        val goal = 10
        b.progressDailyGoal.max      = goal
        b.progressDailyGoal.progress = min(todayCount, goal)
        b.tvDailyGoalValue.text      = "${min(todayCount, goal)} / $goal cards"

        // Due card info
        b.tvDueValue.text = when {
            dueCount <= 0 -> "No cards due now"
            dueCount == 1 -> "1 card due now"
            else          -> "$dueCount cards due now"
        }
        b.tvDueMeta.text = when {
            bestDue != null && dueCount == bestDue.dueCount -> "Start with ${bestDue.title}."
            bestDue != null                                  -> "Best deck to open: ${bestDue.title}."
            dueCount > 0                                     -> "Due cards found across your library."
            else                                             -> "Review a deck to start your schedule."
        }
        b.cardDue.alpha = if (bestDueDeckId != null || canContinueStudy) 1f else 0.85f

        // Recent deck card
        if (recent == null) {
            b.tvRecentDeck.text = "No recent study session"
            b.tvRecentMeta.text = "Start with a deck from your library."
            b.tvRecentHint.text = if (bestDue != null)
                "You already have due cards waiting in ${bestDue.title}."
            else
                "Your last study session will appear here."
        } else {
            b.tvRecentDeck.text = recent.title
            b.tvRecentMeta.text = "Last studied ${formatTime(recent.lastStudiedAt)}"
            b.tvRecentHint.text = "Tap here to continue from your recent deck."
        }

        // Most studied card
        if (most == null) {
            b.tvMostDeck.text = "No favorite deck yet"
            b.tvMostMeta.text = "Your most-studied deck will appear here."
        } else {
            b.tvMostDeck.text = most.title
            b.tvMostMeta.text = if (most.studyCount == 1) "1 study session"
            else "${most.studyCount} study sessions"
        }

        // Greeting / subtitle
        b.tvSubtitle.text = buildString {
            append("Welcome back, $userName. Keep your learning progress going today.")
            if (summary.hasAny) {
                append("\nAchievements: ${summary.unlockedCount}/${summary.totalCount} unlocked")
                if (!summary.nextTitle.isNullOrBlank() && !summary.nextProgressText.isNullOrBlank()) {
                    append(" • Next: ${summary.nextTitle} (${summary.nextProgressText})")
                }
            }
        }

        // Primary action button
        primaryAction = when {
            bestDueDeckId != null -> PrimaryAction.STUDY_DUE
            canContinueStudy      -> PrimaryAction.CONTINUE_STUDY
            else                  -> PrimaryAction.OPEN_LIBRARY
        }
        b.btnContinue.text = when (primaryAction) {
            PrimaryAction.STUDY_DUE      -> "Study Due Cards"
            PrimaryAction.CONTINUE_STUDY -> "Continue Study"
            PrimaryAction.OPEN_LIBRARY   -> "Open Library"
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────

    private fun openPrimaryAction() {
        when (primaryAction) {
            PrimaryAction.STUDY_DUE      -> openBestDueDeck()
            PrimaryAction.CONTINUE_STUDY -> openContinueDeck()
            PrimaryAction.OPEN_LIBRARY   -> (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        }
    }

    private fun openContinueDeck() {
        val recentId = recentDeckId
        if (recentId != null) {
            openDeckWithPremiumCheck(recentId, recentDeckPremium)
            return
        }
        val mostId = mostStudiedDeckId
        if (mostId != null) {
            openDeckWithPremiumCheck(mostId, mostStudiedDeckPremium)
            return
        }
        (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        Snackbar.make(b.root, "No deck yet. Create one in Library first.", Snackbar.LENGTH_SHORT).show()
    }

    private fun openBestDueDeck() {
        val dueDeckId = bestDueDeckId
        if (dueDeckId != null) {
            openDeckWithPremiumCheck(dueDeckId, bestDueDeckPremium)
            return
        }
        if (canContinueStudy) {
            Snackbar.make(b.root, "No due cards yet. Opening your recent deck instead.", Snackbar.LENGTH_SHORT).show()
            openContinueDeck()
            return
        }
        (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        Snackbar.make(b.root, "No deck found. Create one in Library first.", Snackbar.LENGTH_SHORT).show()
    }

    private fun openMostStudiedDeck() {
        val deckId = mostStudiedDeckId ?: run {
            Snackbar.make(b.root, "No favorite deck yet.", Snackbar.LENGTH_SHORT).show()
            return
        }
        openDeckWithPremiumCheck(deckId, mostStudiedDeckPremium)
    }

    private fun openDeckWithPremiumCheck(deckId: Long, isPremium: Boolean) {
        if (isPremium && !isPremiumUser) {
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deckId)
            )
        } else {
            startActivity(
                Intent(requireContext(), StudyActivity::class.java)
                    .putExtra(StudyActivity.EXTRA_DECK_ID, deckId)
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}