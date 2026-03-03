package com.example.stardeckapplication.ui.study

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityStudyBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.math.abs
import kotlin.random.Random

class StudyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID = "extra_deck_id"

        private const val S_INDEX = "s_index"
        private const val S_SHOW_BACK = "s_show_back"
        private const val S_KNOWN = "s_known"
        private const val S_HARD = "s_hard"
        private const val S_SHUFFLED = "s_shuffled"
        private const val S_SEED = "s_seed"
        private const val S_ORDER_IDS = "s_order_ids"
    }

    private data class LastAction(
        val indexBefore: Int,
        val knownBefore: Int,
        val hardBefore: Int,
        val orderIds: LongArray,
        val sessionId: Long,
        val deckId: Long
    )

    private lateinit var b: ActivityStudyBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var userId: Long = -1L
    private var deckId: Long = -1L

    private var baseCards: List<StarDeckDbHelper.CardRow> = emptyList()
    private var order: MutableList<StarDeckDbHelper.CardRow> = mutableListOf()

    private var index: Int = 0
    private var showBack: Boolean = false

    private var knownCount: Int = 0
    private var hardCount: Int = 0

    private var shuffled: Boolean = false
    private var shuffleSeed: Long = 0L

    private var lastAction: LastAction? = null

    private var downX = 0f
    private var downY = 0f
    private val swipeThresholdPx = 120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStudyBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish(); return
        }
        userId = me.id

        deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId <= 0) {
            finish(); return
        }

        val title = db.getDeckTitleForOwner(userId, deckId) ?: run { finish(); return }
        supportActionBar?.title = title

        baseCards = db.getCardsForDeck(userId, deckId)
        if (baseCards.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("No cards")
                .setMessage("This deck has no cards. Create cards first, then study.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
            return
        }

        if (savedInstanceState != null) {
            index = savedInstanceState.getInt(S_INDEX, 0)
            showBack = savedInstanceState.getBoolean(S_SHOW_BACK, false)
            knownCount = savedInstanceState.getInt(S_KNOWN, 0)
            hardCount = savedInstanceState.getInt(S_HARD, 0)
            shuffled = savedInstanceState.getBoolean(S_SHUFFLED, false)
            shuffleSeed = savedInstanceState.getLong(S_SEED, System.currentTimeMillis())
            val ids = savedInstanceState.getLongArray(S_ORDER_IDS)
            order = rebuildOrderFromIds(ids)
        } else {
            shuffleSeed = System.currentTimeMillis()
            order = baseCards.toMutableList()
        }

        if (index < 0) index = 0
        if (index > order.lastIndex) index = order.lastIndex

        b.cardContainer.setOnClickListener { toggleFlip() }
        b.btnShowAnswer.setOnClickListener { toggleFlip() }

        b.cardContainer.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    val isHorizontal = abs(dx) > abs(dy)

                    if (isHorizontal && abs(dx) > swipeThresholdPx) {
                        if (dx < 0) onSwipeLeft() else onSwipeRight()
                        true
                    } else {
                        v.performClick()
                        true
                    }
                }
                else -> true
            }
        }

        b.btnKnown.setOnClickListener {
            if (!showBack) return@setOnClickListener
            markKnown()
        }
        b.btnHard.setOnClickListener {
            if (!showBack) return@setOnClickListener
            markHard()
        }

        b.btnShuffle.setOnClickListener { toggleShuffle() }
        b.btnUndo.setOnClickListener { undoLastAction() }

        updateAllUi()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(S_INDEX, index)
        outState.putBoolean(S_SHOW_BACK, showBack)
        outState.putInt(S_KNOWN, knownCount)
        outState.putInt(S_HARD, hardCount)
        outState.putBoolean(S_SHUFFLED, shuffled)
        outState.putLong(S_SEED, shuffleSeed)
        outState.putLongArray(S_ORDER_IDS, order.map { it.id }.toLongArray())
    }

    // LEFT = Hard, RIGHT = Known
    private fun onSwipeLeft() {
        if (!showBack) nextQuestion() else markHard()
    }

    private fun onSwipeRight() {
        if (!showBack) prevQuestion() else markKnown()
    }

    private fun nextQuestion() {
        lastAction = null
        if (index < order.lastIndex) {
            index++
            showBack = false
            updateAllUi()
        } else {
            Snackbar.make(b.root, "This is the last card", Snackbar.LENGTH_SHORT).show()
            updateUndoUi()
        }
    }

    private fun prevQuestion() {
        lastAction = null
        if (index > 0) {
            index--
            showBack = false
            updateAllUi()
        } else {
            Snackbar.make(b.root, "This is the first card", Snackbar.LENGTH_SHORT).show()
            updateUndoUi()
        }
    }

    private fun toggleFlip() {
        showBack = !showBack
        updateCardUi()
        updateButtonsUi()
    }

    private fun markKnown() {
        val sessionId = db.logStudyResult(userId, deckId, DbContract.RESULT_KNOWN)
        lastAction = snapshotLastAction(sessionId)

        knownCount++
        showUndoSnackbar("Marked Known")
        nextOrFinish()
    }

    private fun markHard() {
        val sessionId = db.logStudyResult(userId, deckId, DbContract.RESULT_HARD)
        lastAction = snapshotLastAction(sessionId)

        hardCount++
        showUndoSnackbar("Marked Hard")
        nextOrFinish()
    }

    private fun snapshotLastAction(sessionId: Long): LastAction {
        return LastAction(
            indexBefore = index,
            knownBefore = knownCount,
            hardBefore = hardCount,
            orderIds = order.map { it.id }.toLongArray(),
            sessionId = sessionId,
            deckId = deckId
        )
    }

    private fun showUndoSnackbar(message: String) {
        updateUndoUi()
        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG)
            .setAction("Undo") { undoLastAction() }
            .show()
    }

    private fun undoLastAction() {
        val a = lastAction ?: run {
            Snackbar.make(b.root, "Nothing to undo", Snackbar.LENGTH_SHORT).show()
            return
        }

        // remove the logged study row (keeps analytics accurate)
        if (a.sessionId > 0) {
            db.deleteStudySession(userId, a.sessionId)
        }

        order = rebuildOrderFromIds(a.orderIds)
        knownCount = a.knownBefore
        hardCount = a.hardBefore
        index = a.indexBefore.coerceIn(0, order.lastIndex)

        showBack = true
        lastAction = null

        updateAllUi()
        Snackbar.make(b.root, "Undone", Snackbar.LENGTH_SHORT).show()
    }

    private fun nextOrFinish() {
        if (index < order.lastIndex) {
            index++
            showBack = false
            updateAllUi()
        } else {
            showSummary()
        }
    }

    private fun showSummary() {
        val total = order.size
        val studied = (knownCount + hardCount).coerceAtMost(total)

        MaterialAlertDialogBuilder(this)
            .setTitle("Session complete")
            .setMessage("Studied: $studied / $total\nKnown: $knownCount\nHard: $hardCount")
            .setPositiveButton("Restart") { _, _ -> restartSession() }
            .setNegativeButton("Back") { _, _ -> finish() }
            .show()
    }

    private fun toggleShuffle() {
        lastAction = null

        MaterialAlertDialogBuilder(this)
            .setTitle("Shuffle")
            .setMessage("Restart the session with shuffle ${if (shuffled) "OFF" else "ON"}?")
            .setPositiveButton("Restart") { _, _ ->
                shuffled = !shuffled
                shuffleSeed = System.currentTimeMillis()
                restartSession()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartSession() {
        knownCount = 0
        hardCount = 0
        index = 0
        showBack = false
        lastAction = null

        order = if (shuffled) baseCards.shuffled(Random(shuffleSeed)).toMutableList()
        else baseCards.toMutableList()

        updateAllUi()
    }

    private fun updateAllUi() {
        updateTopUi()
        updateCardUi()
        updateButtonsUi()
        updateUndoUi()
    }

    private fun updateTopUi() {
        val total = order.size
        val left = total - index
        b.tvCardsLeft.text = "$left cards left"

        val shuffleText = if (shuffled) "On" else "Off"
        b.tvStats.text = "🔥 $hardCount   ✅ $knownCount   🔀 $shuffleText"
    }

    private fun updateCardUi() {
        val card = order[index]
        b.tvFront.text = card.front
        b.tvBack.text = card.back

        b.divider.visibility = if (showBack) View.VISIBLE else View.GONE
        b.labelAnswer.visibility = if (showBack) View.VISIBLE else View.GONE
        b.tvBack.visibility = if (showBack) View.VISIBLE else View.GONE
    }

    private fun updateButtonsUi() {
        b.btnShowAnswer.visibility = if (!showBack) View.VISIBLE else View.INVISIBLE
        b.groupRate.visibility = if (showBack) View.VISIBLE else View.INVISIBLE

        b.btnKnown.isEnabled = showBack
        b.btnHard.isEnabled = showBack
    }

    private fun updateUndoUi() {
        val enabled = lastAction != null
        b.btnUndo.isEnabled = enabled
        b.btnUndo.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun rebuildOrderFromIds(ids: LongArray?): MutableList<StarDeckDbHelper.CardRow> {
        if (ids == null || ids.isEmpty()) {
            return if (shuffled) baseCards.shuffled(Random(shuffleSeed)).toMutableList()
            else baseCards.toMutableList()
        }

        val map = baseCards.associateBy { it.id }
        val rebuilt = mutableListOf<StarDeckDbHelper.CardRow>()
        for (id in ids) {
            val c = map[id]
            if (c != null) rebuilt.add(c)
        }

        val existing = rebuilt.map { it.id }.toHashSet()
        for (c in baseCards) if (!existing.contains(c.id)) rebuilt.add(c)
        return rebuilt
    }
}