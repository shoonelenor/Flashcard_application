package com.example.stardeckapplication.ui.study

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityStudyBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.DeckDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StudyDao
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.math.abs
import kotlin.random.Random

class StudyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID          = "extra_deck_id"
        const val EXTRA_READ_ONLY_PUBLIC = "extra_read_only_public"
        private const val S_INDEX              = "s_index"
        private const val S_SHOW_BACK          = "s_show_back"
        private const val S_KNOWN              = "s_known"
        private const val S_HARD               = "s_hard"
        private const val S_SHUFFLED           = "s_shuffled"
        private const val S_SEED               = "s_seed"
        private const val S_ORDER_IDS          = "s_order_ids"
        private const val S_DUE_MODE           = "s_due_mode"
        private const val S_DUE_COUNT_AT_START = "s_due_count_at_start"
    }

    private data class LastAction(
        val indexBefore      : Int,
        val knownBefore      : Int,
        val hardBefore       : Int,
        val orderIds         : LongArray,
        val sessionId        : Long,
        val deckId           : Long,
        val cardId           : Long,
        val previousProgress : StudyDao.CardProgressSnapshot?
    )

    private lateinit var b: ActivityStudyBinding

    private val dbHelper        by lazy { StarDeckDbHelper(this) }
    private val studyDao        by lazy { StudyDao(dbHelper) }
    private val deckDao         by lazy { DeckDao(dbHelper) }
    private val session         by lazy { SessionManager(this) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }

    private var userId: Long = -1L
    private var deckId: Long = -1L

    // true when the user is studying a public deck they do NOT own
    private var readOnlyPublic = false

    private var baseCards : List<DeckDao.CardRow>        = emptyList()
    private var order     : MutableList<DeckDao.CardRow> = mutableListOf()

    private var index      = 0
    private var showBack   = false
    private var knownCount = 0
    private var hardCount  = 0

    private var shuffled    = false
    private var shuffleSeed = 0L

    private var lastAction : LastAction? = null

    private var downX            = 0f
    private var downY            = 0f
    private val swipeThresholdPx = 120

    private var dueMode         = false
    private var dueCountAtStart = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStudyBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) { finish(); return }

        userId         = me.id
        deckId         = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        readOnlyPublic = intent.getBooleanExtra(EXTRA_READ_ONLY_PUBLIC, false)
        if (deckId <= 0L) { safeFinishWithMessage("Invalid deck"); return }

        val title = if (readOnlyPublic) {
            // For public decks we cannot use getDeckTitleForOwner (ownership check fails)
            runCatching { deckDao.getDeckTitle(deckId) }.getOrNull()
        } else {
            runCatching { deckDao.getDeckTitleForOwner(userId, deckId) }.getOrNull()
        }
        if (title.isNullOrBlank()) { safeFinishWithMessage("Deck not found"); return }
        supportActionBar?.title = title

        val loaded = runCatching { loadStudyCardsSafely(); true }.getOrElse { false }
        if (!loaded || baseCards.isEmpty()) {
            safeFinishWithMessage("This deck has no cards or could not be opened.")
            return
        }

        if (savedInstanceState != null) {
            index           = savedInstanceState.getInt(S_INDEX, 0)
            showBack        = savedInstanceState.getBoolean(S_SHOW_BACK, false)
            knownCount      = savedInstanceState.getInt(S_KNOWN, 0)
            hardCount       = savedInstanceState.getInt(S_HARD, 0)
            shuffled        = savedInstanceState.getBoolean(S_SHUFFLED, false)
            shuffleSeed     = savedInstanceState.getLong(S_SEED, System.currentTimeMillis())
            dueMode         = savedInstanceState.getBoolean(S_DUE_MODE, dueMode)
            dueCountAtStart = savedInstanceState.getInt(S_DUE_COUNT_AT_START, dueCountAtStart)
            order           = rebuildOrderFromIds(savedInstanceState.getLongArray(S_ORDER_IDS))
        } else {
            shuffleSeed = System.currentTimeMillis()
            order       = baseCards.toMutableList()
        }

        if (order.isEmpty()) { safeFinishWithMessage("No cards available for this study session."); return }
        index = index.coerceIn(0, order.lastIndex)

        b.cardContainer.setOnClickListener { toggleFlip() }
        b.btnShowAnswer.setOnClickListener { toggleFlip() }

        b.cardContainer.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; true }
                MotionEvent.ACTION_UP   -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > abs(dy) && abs(dx) > swipeThresholdPx) {
                        if (dx < 0) onSwipeLeft() else onSwipeRight()
                    } else {
                        v.performClick()
                    }
                    true
                }
                else -> true
            }
        }

        b.btnKnown.setOnClickListener   { if (showBack) markKnown() }
        b.btnHard.setOnClickListener    { if (showBack) markHard()  }
        b.btnShuffle.setOnClickListener { toggleShuffle() }
        b.btnUndo.setOnClickListener    { undoLastAction() }

        updateAllUi()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(S_INDEX, index)
        outState.putBoolean(S_SHOW_BACK, showBack)
        outState.putInt(S_KNOWN, knownCount)
        outState.putInt(S_HARD, hardCount)
        outState.putBoolean(S_SHUFFLED, shuffled)
        outState.putLong(S_SEED, shuffleSeed)
        outState.putBoolean(S_DUE_MODE, dueMode)
        outState.putInt(S_DUE_COUNT_AT_START, dueCountAtStart)
        val ids = LongArray(order.size)
        for (i in order.indices) ids[i] = order[i].id
        outState.putLongArray(S_ORDER_IDS, ids)
    }

    private fun loadStudyCardsSafely() {
        // For public decks, skip Due Mode (progress rows may not exist yet for this user)
        if (!readOnlyPublic) {
            val dueLoaded = runCatching {
                val count       = studyDao.getDueCountForDeck(userId, deckId)
                dueCountAtStart = count
                dueMode         = count > 0
                if (dueMode) {
                    val dueCards = studyDao.getDueCardsForDeck(userId, deckId)
                    val mapped   = mutableListOf<DeckDao.CardRow>()
                    for (due in dueCards) {
                        mapped.add(DeckDao.CardRow(id = due.id, front = due.front, back = due.back, createdAt = due.createdAt))
                    }
                    baseCards = mapped
                }
            }.isSuccess

            if (!dueLoaded || baseCards.isEmpty()) {
                dueMode         = false
                dueCountAtStart = 0
                baseCards = runCatching { deckDao.getCardsForDeck(userId, deckId) }.getOrDefault(emptyList())
            }
        } else {
            // Public deck — load all cards directly (no ownership filter)
            dueMode         = false
            dueCountAtStart = 0
            baseCards = runCatching { deckDao.getCardsForPublicDeck(deckId) }.getOrDefault(emptyList())
        }

        val modeLabel = when {
            readOnlyPublic -> "Public Deck"
            dueMode        -> "Due Now • $dueCountAtStart card(s)"
            else           -> "Normal Review"
        }
        supportActionBar?.subtitle = modeLabel
    }

    private fun onSwipeLeft()  { if (!showBack) nextQuestion() else markHard()  }
    private fun onSwipeRight() { if (!showBack) prevQuestion() else markKnown() }

    private fun nextQuestion() {
        lastAction = null
        if (index < order.lastIndex) { index++; showBack = false; updateAllUi() }
        else Snackbar.make(b.root, "This is the last card", Snackbar.LENGTH_SHORT).show()
    }

    private fun prevQuestion() {
        lastAction = null
        if (index > 0) { index--; showBack = false; updateAllUi() }
        else Snackbar.make(b.root, "This is the first card", Snackbar.LENGTH_SHORT).show()
    }

    private fun toggleFlip() { showBack = !showBack; updateCardUi(); updateButtonsUi() }

    private fun markKnown() { applyReview(DbContract.RESULT_KNOWN, "✅ Marked Known") }
    private fun markHard()  { applyReview(DbContract.RESULT_HARD,  "🔴 Marked Hard")  }

    private fun applyReview(result: String, message: String) {
        if (order.isEmpty()) return
        val card = order.getOrNull(index) ?: return

        val srsWorked = runCatching {
            val snapshot = studyDao.getCardProgressSnapshot(userId, card.id)

            // Route to the correct SRS function based on deck ownership
            val sessionId = if (readOnlyPublic) {
                studyDao.applySrsReviewForPublicDeck(
                    studyingUserId = userId,
                    deckId         = deckId,
                    cardId         = card.id,
                    result         = result
                )
            } else {
                studyDao.applySrsReview(
                    ownerUserId = userId,
                    deckId      = deckId,
                    cardId      = card.id,
                    result      = result
                )
            }

            if (sessionId > 0L) {
                val ids = LongArray(order.size)
                for (i in order.indices) ids[i] = order[i].id
                lastAction = LastAction(
                    indexBefore      = index,
                    knownBefore      = knownCount,
                    hardBefore       = hardCount,
                    orderIds         = ids,
                    sessionId        = sessionId,
                    deckId           = deckId,
                    cardId           = card.id,
                    previousProgress = snapshot
                )
                true
            } else false
        }.getOrDefault(false)

        if (!srsWorked) {
            val sessionId = runCatching {
                studyDao.logStudyResult(userId, deckId, result)
            }.getOrDefault(-1L)
            if (sessionId <= 0L) {
                Snackbar.make(b.root, "Could not save study result", Snackbar.LENGTH_SHORT).show()
                return
            }
            val ids = LongArray(order.size)
            for (i in order.indices) ids[i] = order[i].id
            lastAction = LastAction(
                indexBefore      = index,
                knownBefore      = knownCount,
                hardBefore       = hardCount,
                orderIds         = ids,
                sessionId        = sessionId,
                deckId           = deckId,
                cardId           = card.id,
                previousProgress = null
            )
        }

        when (result) {
            DbContract.RESULT_KNOWN -> knownCount++
            DbContract.RESULT_HARD  -> hardCount++
        }

        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG)
            .setAction("Undo") { undoLastAction() }
            .show()

        nextOrFinish()
    }

    private fun undoLastAction() {
        val a = lastAction ?: run {
            Snackbar.make(b.root, "Nothing to undo", Snackbar.LENGTH_SHORT).show()
            return
        }
        runCatching { if (a.sessionId > 0L) studyDao.deleteStudySession(userId, a.sessionId) }
        runCatching {
            if (a.previousProgress != null || supportsProgressRestore()) {
                studyDao.restoreCardProgressSnapshot(userId = userId, cardId = a.cardId, snapshot = a.previousProgress)
            }
        }

        order      = rebuildOrderFromIds(a.orderIds)
        knownCount = a.knownBefore
        hardCount  = a.hardBefore

        if (order.isEmpty()) { safeFinishWithMessage("Study session ended."); return }
        index      = a.indexBefore.coerceIn(0, order.lastIndex)
        showBack   = true
        lastAction = null
        updateAllUi()
        Snackbar.make(b.root, "Undone", Snackbar.LENGTH_SHORT).show()
    }

    private fun nextOrFinish() {
        if (index < order.lastIndex) { index++; showBack = false; updateAllUi() }
        else showSummary()
    }

    private fun showSummary() {
        val total    = order.size
        val studied  = (knownCount + hardCount).coerceAtMost(total)
        val skipped  = (total - studied).coerceAtLeast(0)
        val pct      = if (studied > 0) (knownCount * 100 / studied) else 0
        val mode     = when {
            readOnlyPublic -> "Public Deck"
            dueMode        -> "Due Now"
            else           -> "Normal Review"
        }
        val unlocked = runCatching { achievementSync.syncForUser(userId) }.getOrDefault(0)

        val emoji = when {
            pct >= 80 -> "🌟 Excellent!"
            pct >= 60 -> "👍 Good job!"
            pct >= 40 -> "📚 Keep going!"
            else      -> "💪 Keep practicing!"
        }

        val message = buildString {
            append("Mode: $mode\n\n")
            append("📊 Session Results\n")
            append("─────────────────\n")
            append("Cards studied:  $studied / $total\n")
            append("✅ Known:  $knownCount")
            if (studied > 0) append("  (${knownCount * 100 / studied}%)")
            append("\n")
            append("🔴 Hard:   $hardCount")
            if (studied > 0) append("  (${hardCount * 100 / studied}%)")
            append("\n")
            if (skipped > 0) append("⏭ Skipped: $skipped\n")
            append("\nAccuracy: $pct%  $emoji")
            if (unlocked > 0) append("\n\n🏆 $unlocked achievement(s) unlocked!")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Session Complete")
            .setMessage(message)
            .setPositiveButton("Restart") { _, _ -> restartSession() }
            .setNegativeButton("Done")    { _, _ -> finish() }
            .show()
    }

    private fun toggleShuffle() {
        lastAction = null
        MaterialAlertDialogBuilder(this)
            .setTitle("Shuffle")
            .setMessage("Restart the session with shuffle ${if (shuffled) "OFF" else "ON"}?")
            .setPositiveButton("Restart") { _, _ ->
                shuffled    = !shuffled
                shuffleSeed = System.currentTimeMillis()
                restartSession()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartSession() {
        knownCount = 0; hardCount = 0; index = 0; showBack = false; lastAction = null
        order = if (shuffled) baseCards.shuffled(Random(shuffleSeed)).toMutableList()
        else          baseCards.toMutableList()
        if (order.isEmpty()) { safeFinishWithMessage("No cards available for restart."); return }
        updateAllUi()
    }

    private fun updateAllUi() {
        if (order.isEmpty()) return
        updateTopUi(); updateCardUi(); updateButtonsUi(); updateUndoUi()
    }

    private fun updateTopUi() {
        val left        = (order.size - index).coerceAtLeast(1)
        val shuffleText = if (shuffled) "On"  else "Off"
        val modeText    = when {
            readOnlyPublic -> "Public"
            dueMode        -> "Due"
            else           -> "All"
        }
        b.tvCardsLeft.text = "$left cards left"
        b.tvStats.text     = "Known $knownCount • Hard $hardCount • Shuffle $shuffleText • $modeText"
    }

    private fun updateCardUi() {
        val card = order.getOrNull(index) ?: return
        b.tvFront.text           = card.front
        b.tvBack.text            = card.back
        b.divider.visibility     = if (showBack) View.VISIBLE else View.GONE
        b.labelAnswer.visibility = if (showBack) View.VISIBLE else View.GONE
        b.tvBack.visibility      = if (showBack) View.VISIBLE else View.GONE
    }

    private fun updateButtonsUi() {
        b.btnShowAnswer.visibility = if (!showBack) View.VISIBLE else View.INVISIBLE
        b.groupRate.visibility     = if (showBack)  View.VISIBLE else View.INVISIBLE
        b.btnKnown.isEnabled       = showBack
        b.btnHard.isEnabled        = showBack
    }

    private fun updateUndoUi() {
        val enabled     = lastAction != null
        b.btnUndo.isEnabled = enabled
        b.btnUndo.alpha     = if (enabled) 1.0f else 0.5f
    }

    private fun rebuildOrderFromIds(ids: LongArray?): MutableList<DeckDao.CardRow> {
        if (baseCards.isEmpty()) return mutableListOf()
        if (ids == null || ids.isEmpty()) {
            return if (shuffled) baseCards.shuffled(Random(shuffleSeed)).toMutableList()
            else                 baseCards.toMutableList()
        }
        val map     = mutableMapOf<Long, DeckDao.CardRow>()
        for (card in baseCards) map[card.id] = card
        val rebuilt = mutableListOf<DeckDao.CardRow>()
        for (id in ids) { val card = map[id]; if (card != null) rebuilt.add(card) }
        val seen = HashSet<Long>()
        for (card in rebuilt)   seen.add(card.id)
        for (card in baseCards) { if (!seen.contains(card.id)) rebuilt.add(card) }
        return rebuilt
    }

    private fun supportsProgressRestore(): Boolean =
        runCatching { studyDao.getCardProgressSnapshot(userId, -1L); true }.getOrDefault(false)

    private fun safeFinishWithMessage(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Study")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
