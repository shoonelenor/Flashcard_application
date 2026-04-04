package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityImportDeckBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar

class ImportDeckActivity : AppCompatActivity() {

    private lateinit var b: ActivityImportDeckBinding

    private val session by lazy { SessionManager(this) }
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }
    private val cardDao by lazy { CardDao(dbHelper) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }

    private var parsedCards: List<Pair<String, String>> = emptyList()

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityImportDeckBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        setupToolbar()
        setupLivePreview()

        b.btnImport.setOnClickListener { attemptImport(me.id) }
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
        supportActionBar?.title = "Import Flashcards"
        b.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupLivePreview() {
        b.etImport.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString().orEmpty()
                parsedCards = parseCards(raw)
                b.tvCardCount.text = when (parsedCards.size) {
                    0 -> "No cards detected yet"
                    1 -> "1 card detected"
                    else -> "${parsedCards.size} cards detected"
                }
                if (b.etDeckTitle.text.isNullOrBlank()) {
                    b.etDeckTitle.setText(guessDeckTitle(parsedCards))
                }
            }
        })
    }

    // ─────────────────────────────────────────────────────────────
    // Import logic
    // ─────────────────────────────────────────────────────────────

    private fun attemptImport(userId: Long) {
        b.tilImport.error = null
        b.tilDeckTitle.error = null

        val raw = b.etImport.text?.toString().orEmpty().trim()
        val title = b.etDeckTitle.text?.toString().orEmpty().trim()

        if (raw.isBlank()) {
            b.tilImport.error = "Please paste some text first"
            return
        }

        parsedCards = parseCards(raw)

        if (parsedCards.size < 2) {
            b.tilImport.error = "Need at least 2 valid lines (e.g. Term - Definition)"
            return
        }

        if (title.isBlank()) {
            b.tilDeckTitle.error = "Deck title is required"
            return
        }

        if (title.length > 40) {
            b.tilDeckTitle.error = "Max 40 characters"
            return
        }

        saveDeck(userId, title, parsedCards)
    }

    private fun saveDeck(userId: Long, title: String, cards: List<Pair<String, String>>) {
        val deckId = runCatching {
            deckDao.createDeck(userId, title, "Imported from text", false, false)
        }.getOrDefault(-1L)

        if (deckId <= 0L) {
            Snackbar.make(b.root, "Could not create deck. Try a different title.", Snackbar.LENGTH_LONG).show()
            return
        }

        var created = 0
        for ((front, back) in cards) {
            val id = runCatching { cardDao.createCardAny(deckId, front, back) }.getOrDefault(-1L)
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

    // ─────────────────────────────────────────────────────────────
    // Parsing helpers
    // ─────────────────────────────────────────────────────────────

    private val separators = listOf("\t", " | ", "|", " - ", " – ", " — ", " : ", ": ")

    private fun parseCards(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim().removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val pair = separators.firstNotNullOfOrNull { sep ->
                    val i = line.indexOf(sep)
                    if (i <= 0) null
                    else {
                        val front = line.substring(0, i).trim()
                        val back = line.substring(i + sep.length).trim()
                        if (front.isNotBlank() && back.isNotBlank()) front to back else null
                    }
                }
                pair
            }
            .distinct()
            .toList()
    }

    private fun guessDeckTitle(cards: List<Pair<String, String>>): String {
        val first = cards.firstOrNull()?.first.orEmpty().trim()
        return if (first.isNotBlank() && first.length <= 24) "$first Deck" else "Imported Deck"
    }
}