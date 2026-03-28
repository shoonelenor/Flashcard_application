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
import com.example.stardeckapplication.ui.study.StudyActivity
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

    private val session  by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val statsDao by lazy { StatsDao(dbHelper) }
    private val studyDao by lazy { StudyDao(dbHelper) }   // ✅ getDueCountForUser
    private val userDao  by lazy { UserDao(dbHelper) }    // ✅ isUserPremium
    private val deckDao  by lazy { UserDeckDao(dbHelper) }
    private val cardDao  by lazy { CardDao(dbHelper) }

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
            if (canContinueStudy) openContinueDeck()
            else (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
        }
        b.cardRecent.setOnClickListener { if (canContinueStudy) openContinueDeck() }
        b.btnNewDeck.setOnClickListener {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            Snackbar.make(b.root, "Open Library and tap + to create a deck.", Snackbar.LENGTH_SHORT).show()
        }
        b.btnImportHome.setOnClickListener {
            Snackbar.make(b.root, "Import feature is coming. For now, you can manually create decks.", Snackbar.LENGTH_SHORT).show()
        }
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

        val todayCount                           = statsDao.getTodayStudyCount(me.id)
        val streakDays                           = statsDao.getStudyStreakDays(me.id)
        val recent: StatsDao.RecentDeckRow?      = statsDao.getRecentlyStudiedDeck(me.id)
        val most:   StatsDao.MostStudiedDeckRow? = statsDao.getMostStudiedDeck(me.id)

        b.tvTodayValue.text = if (todayCount == 1) "1 card reviewed" else "$todayCount cards reviewed"
        b.tvStreakValue.text = if (streakDays == 1) "1 day" else "$streakDays days"

        val goal = 10
        b.progressDailyGoal.max      = goal
        b.progressDailyGoal.progress = min(todayCount, goal)
        b.tvDailyGoalValue.text      = "${min(todayCount, goal)} / $goal cards"

        // ✅ StudyDao.getDueCountForUser — correct home
        val dueCount: Int = try { studyDao.getDueCountForUser(me.id) } catch (e: Exception) { 0 }
        b.tvDueValue.text = when {
            dueCount <= 0 -> "No cards due now"
            dueCount == 1 -> "1 card due now"
            else          -> "$dueCount cards due now"
        }

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

        canContinueStudy   = (recent != null || most != null)
        b.btnContinue.text = if (canContinueStudy) "Continue Study" else "Open Library"
    }

    private fun showAiGeneratorDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            hint     = "Paste your notes here.\nExample:\nPhotosynthesis - process by which plants make food."
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
                } catch (e: Exception) { emptyList() }

                if (cards.isEmpty())
                    Snackbar.make(b.root, "Could not find flashcards. Try clearer notes (Term - definition).", Snackbar.LENGTH_LONG).show()
                else
                    showAiResultDialog(cards)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAiResultDialog(cards: List<RuleBasedFlashcardGenerator.GeneratedCard>) {
        val first  = cards.first()
        val sample = "Example:\nQ: ${first.front}\nA: ${first.back}"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Generated ${cards.size} card(s)")
            .setMessage(sample)
            .setPositiveButton("Save as new deck") { _, _ -> showSaveAiDeckDialog(cards) }
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
                    val t    = d.etTitle.text?.toString().orEmpty().trim()
                    val desc = d.etDescription.text?.toString()

                    when {
                        t.isBlank()   -> { d.tilTitle.error = "Title is required"; return@setOnClickListener }
                        t.length > 40 -> { d.tilTitle.error = "Max 40 characters";  return@setOnClickListener }
                    }

                    val deckId: Long = try {
                        deckDao.createDeck(me.id, t, desc, false, false)
                    } catch (e: Exception) { -1L }

                    if (deckId <= 0L) {
                        Snackbar.make(b.root, "Could not create deck. Try a different title.", Snackbar.LENGTH_LONG).show()
                        return@setOnClickListener
                    }

                    var created = 0
                    for (card in cards) {
                        val id: Long = try { cardDao.createCardAny(deckId, card.front, card.back) } catch (e: Exception) { -1L }
                        if (id > 0L) created++
                    }

                    Snackbar.make(b.root, "AI deck created with $created card(s).", Snackbar.LENGTH_LONG).show()
                    startActivity(
                        Intent(requireContext(), DeckCardsActivity::class.java)
                            .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deckId)
                    )
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    private fun openContinueDeck() {
        val me     = session.load() ?: return
        val recent = statsDao.getRecentlyStudiedDeck(me.id)
        val most   = statsDao.getMostStudiedDeck(me.id)
        val deckId: Long = recent?.deckId ?: most?.deckId ?: return
        startActivity(
            Intent(requireContext(), StudyActivity::class.java)
                .putExtra(StudyActivity.EXTRA_DECK_ID, deckId)
        )
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}