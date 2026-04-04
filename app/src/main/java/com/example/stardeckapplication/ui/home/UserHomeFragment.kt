package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.DialogEditDeckBinding
import com.example.stardeckapplication.databinding.FragmentUserHomeBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StatsDao
import com.example.stardeckapplication.db.StudyDao
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.ui.study.StudyActivity
import com.example.stardeckapplication.util.AchievementSummaryHelper
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.RuleBasedFlashcardGenerator
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class UserHomeFragment : Fragment(R.layout.fragment_user_home) {

    private var _b: FragmentUserHomeBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }
    private val achievementSummary by lazy { AchievementSummaryHelper(dbHelper) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val statsDao by lazy { StatsDao(dbHelper) }
    private val studyDao by lazy { StudyDao(dbHelper) }
    private val userDao by lazy { UserDao(dbHelper) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }
    private val cardDao by lazy { CardDao(dbHelper) }

    private data class DueDeckCandidate(
        val deckId: Long,
        val title: String,
        val dueCount: Int,
        val isPremium: Boolean,
        val isRecent: Boolean
    )

    private enum class PrimaryAction {
        OPEN_LIBRARY,
        CONTINUE_STUDY,
        STUDY_DUE
    }

    private var canContinueStudy = false
    private var isPremiumUser = false
    private var recentDeckId: Long? = null
    private var recentDeckPremium = false
    private var mostStudiedDeckId: Long? = null
    private var mostStudiedDeckPremium = false
    private var bestDueDeckId: Long? = null
    private var bestDueDeckPremium = false
    private var primaryAction = PrimaryAction.OPEN_LIBRARY

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserHomeBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }


        b.btnContinue.setOnClickListener { openPrimaryAction() }

        b.cardRecent.setOnClickListener {
            if (canContinueStudy) {
                openContinueDeck()
            } else {
                Snackbar.make(b.root, "No recent study session yet.", Snackbar.LENGTH_SHORT).show()
            }
        }

        b.cardDue.setOnClickListener { openBestDueDeck() }
        b.cardMost.setOnClickListener { openMostStudiedDeck() }

        b.btnNewDeck.setOnClickListener {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            Snackbar.make(
                b.root,
                "Open Library and tap + to create a deck.",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        b.btnImportHome.setOnClickListener { showImportDialog() }
        b.btnAiHome.setOnClickListener { showAiGeneratorDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun refresh() {
        val me = session.load() ?: return

        achievementSync.syncForUser(me.id)
        val summary = achievementSummary.getSummary(me.id)

        val todayCount = runCatching { statsDao.getTodayStudyCount(me.id) }.getOrDefault(0)
        val streakDays = runCatching { statsDao.getStudyStreakDays(me.id) }.getOrDefault(0)
        val recent = runCatching { statsDao.getRecentlyStudiedDeck(me.id) }.getOrNull()
        val most = runCatching { statsDao.getMostStudiedDeck(me.id) }.getOrNull()
        val dueCount = runCatching { studyDao.getDueCountForUser(me.id) }.getOrDefault(0)

        val decks = runCatching { deckDao.getDecksForOwner(me.id) }
            .getOrDefault(emptyList())
            .filter { it.status == DbContract.DECK_ACTIVE }

        val deckById = decks.associateBy { it.id }

        isPremiumUser = runCatching { userDao.isUserPremium(me.id) }.getOrDefault(false)

        recentDeckId = recent?.deckId
        recentDeckPremium = recent?.deckId?.let { deckById[it]?.isPremium } == true

        mostStudiedDeckId = most?.deckId
        mostStudiedDeckPremium = most?.deckId?.let { deckById[it]?.isPremium } == true

        val dueCandidates = decks.mapNotNull { deck ->
            val count = runCatching { studyDao.getDueCountForDeck(me.id, deck.id) }.getOrDefault(0)

            if (count <= 0) return@mapNotNull null
            if (deck.isPremium && !isPremiumUser) return@mapNotNull null

            DueDeckCandidate(
                deckId = deck.id,
                title = deck.title,
                dueCount = count,
                isPremium = deck.isPremium,
                isRecent = recent?.deckId == deck.id
            )
        }

        val bestDue = dueCandidates
            .sortedWith(
                compareByDescending<DueDeckCandidate> { it.dueCount }
                    .thenByDescending { it.isRecent }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
            .firstOrNull()

        bestDueDeckId = bestDue?.deckId
        bestDueDeckPremium = bestDue?.isPremium == true
        canContinueStudy = recent != null || most != null

        b.tvTodayValue.text =
            if (todayCount == 1) "1 card reviewed" else "$todayCount cards reviewed"

        b.tvStreakValue.text =
            if (streakDays == 1) "1 day" else "$streakDays days"

        val goal = 10
        b.progressDailyGoal.max = goal
        b.progressDailyGoal.progress = min(todayCount, goal)
        b.tvDailyGoalValue.text = "${min(todayCount, goal)} / $goal cards"

        b.tvDueValue.text = when {
            dueCount <= 0 -> "No cards due now"
            dueCount == 1 -> "1 card due now"
            else -> "$dueCount cards due now"
        }

        b.tvDueMeta.text = when {
            bestDue != null && dueCount == bestDue.dueCount -> "Start with ${bestDue.title}."
            bestDue != null -> "Best deck to open: ${bestDue.title}."
            dueCount > 0 -> "Due cards found across your library."
            else -> "Review a deck to start your schedule."
        }

        b.cardDue.alpha = if (bestDueDeckId != null || canContinueStudy) 1f else 0.85f

        if (recent == null) {
            b.tvRecentDeck.text = "No recent study session"
            b.tvRecentMeta.text = "Start with a deck from your library."
            b.tvRecentHint.text = if (bestDue != null) {
                "You already have due cards waiting in ${bestDue.title}."
            } else {
                "Your last study session will appear here."
            }
        } else {
            b.tvRecentDeck.text = recent.title
            b.tvRecentMeta.text = "Last studied ${formatTime(recent.lastStudiedAt)}"
            b.tvRecentHint.text = "Tap here to continue from your recent deck."
        }

        if (most == null) {
            b.tvMostDeck.text = "No favorite deck yet"
            b.tvMostMeta.text = "Your most-studied deck will appear here."
        } else {
            b.tvMostDeck.text = most.title
            b.tvMostMeta.text = if (most.studyCount == 1) {
                "1 study session"
            } else {
                "${most.studyCount} study sessions"
            }
        }

        b.tvSubtitle.text = buildString {
            append("Welcome back, ${me.name}. Keep your learning progress going today.")
            if (summary.hasAny) {
                append("\nAchievements: ${summary.unlockedCount}/${summary.totalCount} unlocked")
                if (!summary.nextTitle.isNullOrBlank() && !summary.nextProgressText.isNullOrBlank()) {
                    append(" • Next: ${summary.nextTitle} (${summary.nextProgressText})")
                }
            }
        }

        primaryAction = when {
            bestDueDeckId != null -> PrimaryAction.STUDY_DUE
            canContinueStudy -> PrimaryAction.CONTINUE_STUDY
            else -> PrimaryAction.OPEN_LIBRARY
        }

        b.btnContinue.text = when (primaryAction) {
            PrimaryAction.STUDY_DUE -> "Study Due Cards"
            PrimaryAction.CONTINUE_STUDY -> "Continue Study"
            PrimaryAction.OPEN_LIBRARY -> "Open Library"
        }
    }

    private fun openPrimaryAction() {
        when (primaryAction) {
            PrimaryAction.STUDY_DUE -> openBestDueDeck()
            PrimaryAction.CONTINUE_STUDY -> openContinueDeck()
            PrimaryAction.OPEN_LIBRARY -> (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        }
    }

    private fun openContinueDeck() {
        val recentId = recentDeckId
        if (recentId != null) {
            if (recentDeckPremium && !isPremiumUser) {
                openPremiumDemo(recentId)
            } else {
                openStudyDeck(recentId)
            }
            return
        }

        val mostId = mostStudiedDeckId
        if (mostId != null) {
            if (mostStudiedDeckPremium && !isPremiumUser) {
                openPremiumDemo(mostId)
            } else {
                openStudyDeck(mostId)
            }
            return
        }

        (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        Snackbar.make(
            b.root,
            "No deck yet. Create one in Library first.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun openBestDueDeck() {
        val dueDeckId = bestDueDeckId
        if (dueDeckId != null) {
            if (bestDueDeckPremium && !isPremiumUser) {
                openPremiumDemo(dueDeckId)
            } else {
                openStudyDeck(dueDeckId)
            }
            return
        }

        if (canContinueStudy) {
            Snackbar.make(
                b.root,
                "No due cards yet. Opening your continue deck instead.",
                Snackbar.LENGTH_SHORT
            ).show()
            openContinueDeck()
            return
        }

        (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        Snackbar.make(
            b.root,
            "No deck found. Create one in Library first.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun openMostStudiedDeck() {
        val deckId = mostStudiedDeckId
        if (deckId == null) {
            Snackbar.make(b.root, "No favorite deck yet.", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (mostStudiedDeckPremium && !isPremiumUser) {
            openPremiumDemo(deckId)
        } else {
            openStudyDeck(deckId)
        }
    }

    private fun openStudyDeck(deckId: Long) {
        startActivity(
            Intent(requireContext(), StudyActivity::class.java)
                .putExtra(StudyActivity.EXTRA_DECK_ID, deckId)
        )
    }

    private fun openPremiumDemo(deckId: Long) {
        startActivity(
            Intent(requireContext(), PremiumDemoActivity::class.java)
                .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deckId)
        )
    }

    private fun showImportDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            hint = "Paste one card per line.\nExamples:\nPhotosynthesis - Process plants use to make food\nCPU | Central Processing Unit"
            minLines = 6
            maxLines = 12
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Import flashcards from text")
            .setMessage("Supported separators: -, :, | or tab. One card per line.")
            .setView(input)
            .setPositiveButton("Preview", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
                            val raw = input.text?.toString().orEmpty().trim()
                            val parsed = parseImportedCards(raw)

                            if (parsed.size < 2) {
                                Snackbar.make(
                                    b.root,
                                    "Could not find enough cards. Add at least 2 valid lines like Term - Definition.",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                return@setOnClickListener
                            }

                            dialog.dismiss()
                            showImportPreviewDialog(parsed)
                        }
                }
                dialog.show()
            }
    }

    private fun showImportPreviewDialog(cards: List<Pair<String, String>>) {
        val sample = cards.take(3).joinToString("\n\n") { (front, back) ->
            "Q: $front\nA: $back"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Imported ${cards.size} card(s)")
            .setMessage(sample)
            .setPositiveButton("Save as new deck") { _, _ ->
                showSaveImportedDeckDialog(cards)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSaveImportedDeckDialog(cards: List<Pair<String, String>>) {
        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            Snackbar.make(b.root, "You must be logged in as a user.", Snackbar.LENGTH_LONG).show()
            return
        }

        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Save Imported Deck"
        d.etTitle.setText(guessDeckTitle(cards))
        d.etDescription.setText("Imported from text")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error = null

                    val title = d.etTitle.text?.toString().orEmpty().trim()
                    val desc = d.etDescription.text?.toString().orEmpty().trim()

                    when {
                        title.isBlank() -> {
                            d.tilTitle.error = "Title is required"
                            return@setOnClickListener
                        }

                        title.length > 40 -> {
                            d.tilTitle.error = "Max 40 characters"
                            return@setOnClickListener
                        }
                    }

                    val deckId = runCatching {
                        deckDao.createDeck(
                            me.id,
                            title,
                            if (desc.isBlank()) null else desc,
                            false,
                            false
                        )
                    }.getOrDefault(-1L)

                    if (deckId <= 0L) {
                        Snackbar.make(
                            b.root,
                            "Could not create deck. Try a different title.",
                            Snackbar.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    var created = 0
                    for ((front, back) in cards) {
                        val id = runCatching {
                            cardDao.createCardAny(deckId, front, back)
                        }.getOrDefault(-1L)

                        if (id > 0L) created++
                    }

                    val unlocked = achievementSync.syncForUser(me.id)
                    val message = if (unlocked > 0) {
                        "Imported deck created with $created card(s) • $unlocked achievement(s) unlocked!"
                    } else {
                        "Imported deck created with $created card(s)."
                    }

                    Snackbar.make(
                        b.root,
                        message,
                        Snackbar.LENGTH_LONG
                    ).show()

                    startActivity(
                        Intent(requireContext(), DeckCardsActivity::class.java)
                            .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deckId)
                    )

                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun parseImportedCards(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()

        val separators = listOf("\t", " | ", "|", " - ", " – ", " — ", " : ", ": ")

        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cleaned = line.removePrefix("- ").removePrefix("• ").trim()
                val pair = splitLineToCard(cleaned, separators) ?: return@mapNotNull null
                val front = pair.first.trim()
                val back = pair.second.trim()

                if (front.isBlank() || back.isBlank()) {
                    null
                } else {
                    front to back
                }
            }
            .distinct()
            .toList()
    }

    private fun splitLineToCard(
        line: String,
        separators: List<String>
    ): Pair<String, String>? {
        for (separator in separators) {
            val index = line.indexOf(separator)
            if (index <= 0) continue

            val left = line.substring(0, index).trim()
            val right = line.substring(index + separator.length).trim()

            if (left.isNotBlank() && right.isNotBlank()) {
                return left to right
            }
        }
        return null
    }

    private fun guessDeckTitle(cards: List<Pair<String, String>>): String {
        val firstFront = cards.firstOrNull()?.first.orEmpty().trim()

        if (firstFront.isBlank()) return "Imported Deck"

        return if (firstFront.length <= 24) {
            "$firstFront Deck"
        } else {
            "Imported Deck"
        }
    }

    private fun showAiGeneratorDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            hint = "Paste your notes here.\nExample:\nPhotosynthesis - process by which plants make food."
            minLines = 4
            maxLines = 8
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Generate flashcards from text")
            .setMessage("Paste your notes. The app will try to create draft Q&A cards.")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val text = input.text?.toString().orEmpty().trim()

                if (text.length < 10) {
                    Snackbar.make(b.root, "Please paste a bit more text.", Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val cards: List<RuleBasedFlashcardGenerator.GeneratedCard> = try {
                    RuleBasedFlashcardGenerator().generate(text)
                } catch (e: Exception) {
                    emptyList()
                }

                if (cards.isEmpty()) {
                    Snackbar.make(
                        b.root,
                        "Could not find flashcards. Try clearer notes (Term - definition).",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    showAiResultDialog(cards)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAiResultDialog(cards: List<RuleBasedFlashcardGenerator.GeneratedCard>) {
        val first = cards.first()
        val sample = "Example:\nQ: ${first.front}\nA: ${first.back}"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Generated ${cards.size} card(s)")
            .setMessage(sample)
            .setPositiveButton("Save as new deck") { _, _ ->
                showSaveAiDeckDialog(cards)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSaveAiDeckDialog(cards: List<RuleBasedFlashcardGenerator.GeneratedCard>) {
        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            Snackbar.make(b.root, "You must be logged in as a user.", Snackbar.LENGTH_LONG).show()
            return
        }

        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Save AI Deck"
        d.etTitle.setText("")
        d.etDescription.setText("Generated from notes")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error = null

                    val t = d.etTitle.text?.toString().orEmpty().trim()
                    val desc = d.etDescription.text?.toString()

                    when {
                        t.isBlank() -> {
                            d.tilTitle.error = "Title is required"
                            return@setOnClickListener
                        }

                        t.length > 40 -> {
                            d.tilTitle.error = "Max 40 characters"
                            return@setOnClickListener
                        }
                    }

                    val deckId: Long = try {
                        deckDao.createDeck(me.id, t, desc, false, false)
                    } catch (e: Exception) {
                        -1L
                    }

                    if (deckId <= 0L) {
                        Snackbar.make(
                            b.root,
                            "Could not create deck. Try a different title.",
                            Snackbar.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    var created = 0
                    for (card in cards) {
                        val id: Long = try {
                            cardDao.createCardAny(deckId, card.front, card.back)
                        } catch (e: Exception) {
                            -1L
                        }

                        if (id > 0L) created++
                    }

                    val unlocked = achievementSync.syncForUser(me.id)
                    val message = if (unlocked > 0) {
                        "AI deck created with $created card(s) • $unlocked achievement(s) unlocked!"
                    } else {
                        "AI deck created with $created card(s)."
                    }

                    Snackbar.make(
                        b.root,
                        message,
                        Snackbar.LENGTH_LONG
                    ).show()

                    startActivity(
                        Intent(requireContext(), DeckCardsActivity::class.java)
                            .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deckId)
                    )

                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    private fun formatTime(ms: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }
}