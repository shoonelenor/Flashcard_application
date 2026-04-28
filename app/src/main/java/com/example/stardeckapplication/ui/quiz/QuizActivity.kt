package com.example.stardeckapplication.ui.quiz

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityQuizBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.DeckDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID = "extra_deck_id"
        private const val MIN_CARDS = 2
    }

    private lateinit var b: ActivityQuizBinding

    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val deckDao  by lazy { DeckDao(dbHelper) }
    private val session  by lazy { SessionManager(this) }

    private var deckId: Long = -1L

    private var allCards: List<DeckDao.CardRow> = emptyList()
    private var questions: List<QuizQuestion>   = emptyList()
    private var currentIndex = 0
    private var score        = 0
    private var answered     = false

    private val optionButtons get() = listOf(b.btnOption1, b.btnOption2, b.btnOption3, b.btnOption4)

    data class QuizQuestion(
        val questionText: String,
        val correctAnswer: String,
        val options: List<String>   // always 4, shuffled
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Quiz"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) { finish(); return }

        deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId <= 0L) { finish(); return }

        val title = runCatching { deckDao.getDeckTitleForOwner(me.id, deckId) }.getOrNull()
            ?: runCatching { deckDao.getDeckTitle(deckId) }.getOrNull()
        supportActionBar?.subtitle = title ?: ""

        allCards = runCatching { deckDao.getCardsForDeck(me.id, deckId) }.getOrDefault(emptyList())

        if (allCards.size < MIN_CARDS) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Not enough cards")
                .setMessage("You need at least $MIN_CARDS cards in this deck to start a quiz.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        buildQuestions()
        showQuestion()

        b.btnNext.setOnClickListener {
            if (!answered) {
                Snackbar.make(b.root, "Please select an answer first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            currentIndex++
            if (currentIndex < questions.size) {
                showQuestion()
            } else {
                showSummary()
            }
        }
    }

    private fun buildQuestions() {
        val shuffled = allCards.shuffled()
        questions = shuffled.map { card ->
            val wrongPool = allCards.filter { it.id != card.id }.shuffled()
            val wrongAnswers = wrongPool.take(3).map { it.back }
            val options = (wrongAnswers + card.back).shuffled()
            QuizQuestion(
                questionText  = card.front,
                correctAnswer = card.back,
                options       = options
            )
        }
    }

    private fun showQuestion() {
        answered = false
        val q = questions[currentIndex]

        b.tvProgress.text   = "Question ${currentIndex + 1} / ${questions.size}"
        b.progressBar.max   = questions.size
        b.progressBar.progress = currentIndex + 1
        b.tvQuestion.text   = q.questionText
        b.tvFeedback.visibility = View.GONE
        b.btnNext.visibility    = View.GONE

        optionButtons.forEachIndexed { i, btn ->
            btn.text = q.options.getOrElse(i) { "" }
            resetButtonStyle(btn)
            btn.isEnabled = true
            btn.setOnClickListener { onOptionSelected(btn, q) }
        }
    }

    private fun onOptionSelected(selected: Button, q: QuizQuestion) {
        if (answered) return
        answered = true

        val isCorrect = selected.text.toString().trim() == q.correctAnswer.trim()
        if (isCorrect) score++

        optionButtons.forEach { btn ->
            btn.isEnabled = false
            when {
                btn.text.toString().trim() == q.correctAnswer.trim() ->
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388E3C"))  // green
                btn == selected && !isCorrect ->
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))  // red
                else ->
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#757575"))  // grey
            }
        }

        b.tvFeedback.text = if (isCorrect) "✅ Correct!" else "❌ Wrong! Answer: ${q.correctAnswer}"
        b.tvFeedback.setTextColor(
            if (isCorrect) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")
        )
        b.tvFeedback.visibility = View.VISIBLE

        // Auto-show Next button after short delay
        Handler(Looper.getMainLooper()).postDelayed({
            b.btnNext.visibility = View.VISIBLE
            b.btnNext.text = if (currentIndex < questions.lastIndex) "Next →" else "See Results"
        }, 800)
    }

    private fun resetButtonStyle(btn: Button) {
        // Use a neutral Material teal to match app theme
        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#01696F"))
    }

    private fun showSummary() {
        val total = questions.size
        val pct   = if (total > 0) (score * 100 / total) else 0
        val emoji = when {
            pct >= 80 -> "🌟 Excellent!"
            pct >= 60 -> "👍 Good job!"
            pct >= 40 -> "📚 Keep going!"
            else      -> "💪 Keep practicing!"
        }

        val message = buildString {
            append("📊 Quiz Results\n")
            append("────────────────\n")
            append("Score:  $score / $total  ($pct%)\n\n")
            append(emoji)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Quiz Complete")
            .setMessage(message)
            .setPositiveButton("Play Again") { _, _ ->
                currentIndex = 0
                score        = 0
                buildQuestions()
                showQuestion()
            }
            .setNegativeButton("Done") { _, _ -> finish() }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
