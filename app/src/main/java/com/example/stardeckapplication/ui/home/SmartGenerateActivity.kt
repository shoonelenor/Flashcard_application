package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivitySmartGenerateBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.RuleBasedFlashcardGenerator
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar

class SmartGenerateActivity : AppCompatActivity() {

    private lateinit var b: ActivitySmartGenerateBinding

    private val session by lazy { SessionManager(this) }
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }
    private val cardDao by lazy { CardDao(dbHelper) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }

    // ✅ FIXED: added <RuleBasedFlashcardGenerator.GeneratedCard> generic type
    private var generatedCards: List<RuleBasedFlashcardGenerator.GeneratedCard> = emptyList()

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySmartGenerateBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        setupToolbar()

        b.btnGenerate.setOnClickListener { attemptGenerate() }
        b.btnSave.setOnClickListener { attemptSave(me.id) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ─────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Smart Generate"
        b.toolbar.setNavigationOnClickListener { finish() }
    }

    // ─────────────────────────────────────────────────────────────
    // Generate
    // ─────────────────────────────────────────────────────────────

    private fun attemptGenerate() {
        b.tilNotes.error = null

        val text = b.etNotes.text?.toString().orEmpty().trim()

        if (text.length < 10) {
            b.tilNotes.error = "Please paste a bit more text (at least a few sentences)"
            return
        }

        generatedCards = try {
            RuleBasedFlashcardGenerator().generate(text)
        } catch (e: Exception) {
            emptyList()
        }

        if (generatedCards.isEmpty()) {
            b.tilNotes.error = "Could not find flashcards — try clearer notes (Term - Definition)"
            return
        }

        // Show preview
        val preview = generatedCards.take(3).joinToString("\n\n") { "Q: ${it.front}\nA: ${it.back}" }
        val suffix = if (generatedCards.size > 3) "\n\n…and ${generatedCards.size - 3} more" else ""
        b.tvPreview.text = "${generatedCards.size} cards generated:\n\n$preview$suffix"
        b.tvPreview.visibility = View.VISIBLE

        // Auto-fill deck title if empty
        if (b.etDeckTitle.text.isNullOrBlank()) {
            b.etDeckTitle.setText("Generated Deck")
        }

        b.btnSave.visibility = View.VISIBLE
        b.btnGenerate.text = "Re-generate"
    }

    // ─────────────────────────────────────────────────────────────
    // Save
    // ─────────────────────────────────────────────────────────────

    private fun attemptSave(userId: Long) {
        b.tilDeckTitle.error = null

        val title = b.etDeckTitle.text?.toString().orEmpty().trim()

        if (title.isBlank()) {
            b.tilDeckTitle.error = "Deck title is required"
            return
        }

        if (title.length > 40) {
            b.tilDeckTitle.error = "Max 40 characters"
            return
        }

        if (generatedCards.isEmpty()) {
            Snackbar.make(b.root, "Generate cards first.", Snackbar.LENGTH_SHORT).show()
            return
        }

        saveDeck(userId, title)
    }

    private fun saveDeck(userId: Long, title: String) {
        val desc = "Generated from notes"
        val deckId = runCatching {
            deckDao.createDeck(userId, title, desc, false, false)
        }.getOrDefault(-1L)

        if (deckId <= 0L) {
            Snackbar.make(b.root, "Could not create deck. Try a different title.", Snackbar.LENGTH_LONG).show()
            return
        }

        var created = 0
        for (card in generatedCards) {
            val id = runCatching {
                cardDao.createCardAny(deckId, card.front, card.back)
            }.getOrDefault(-1L)
            if (id > 0L) created++
        }

        val unlocked = achievementSync.syncForUser(userId)
        val msg = if (unlocked > 0)
            "Deck created with $created card(s) • $unlocked achievement(s) unlocked!"
        else
            "Deck created with $created card(s)."

        Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()

        startActivity(
            Intent(this, DeckCardsActivity::class.java)
                .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deckId)
        )
        finish()
    }
}