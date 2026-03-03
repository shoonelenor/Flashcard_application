package com.example.stardeckapplication.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityAchievementsBinding
import com.example.stardeckapplication.databinding.ItemAchievementBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import kotlin.math.min

class AchievementsActivity : AppCompatActivity() {

    private lateinit var b: ActivityAchievementsBinding
    private val session by lazy { SessionManager(this) }
    private val db by lazy { StarDeckDbHelper(this) }

    private enum class Metric { DECKS, CARDS, TOTAL_STUDY, TODAY_STUDY, STREAK }

    private data class AchDef(
        val title: String,
        val description: String,
        val metric: Metric,
        val target: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Achievements"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        // Pull stats (offline, safe)
        val today = db.getTodayStudyCount(me.id)
        val streak = db.getStudyStreakDays(me.id)
        val totalStudy = db.getTotalStudyCount(me.id)
        val deckCount = db.getTotalDeckCountAllStatuses(me.id)
        val cardCount = db.getTotalCardCountForOwnerAllStatuses(me.id)

        val defs = listOf(
            AchDef("First Deck", "Create your first deck.", Metric.DECKS, 1),
            AchDef("First Study", "Complete your first card rating.", Metric.TOTAL_STUDY, 1),
            AchDef("Card Creator", "Create 25 cards.", Metric.CARDS, 25),
            AchDef("Consistent Learner", "Maintain a 3-day streak.", Metric.STREAK, 3),
            AchDef("Week Streak", "Maintain a 7-day streak.", Metric.STREAK, 7),
            AchDef("Study 50", "Rate 50 cards in total.", Metric.TOTAL_STUDY, 50),
            AchDef("Study 200", "Rate 200 cards in total.", Metric.TOTAL_STUDY, 200),
            AchDef("Daily Push", "Rate 20 cards in one day.", Metric.TODAY_STUDY, 20)
        )

        val unlockedCount = defs.count { currentFor(it.metric, deckCount, cardCount, totalStudy, today, streak) >= it.target }
        b.tvSummary.text = "$unlockedCount / ${defs.size} unlocked"

        render(defs, deckCount, cardCount, totalStudy, today, streak)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render(
        defs: List<AchDef>,
        deckCount: Int,
        cardCount: Int,
        totalStudy: Int,
        today: Int,
        streak: Int
    ) {
        b.container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        defs.forEach { def ->
            val current = currentFor(def.metric, deckCount, cardCount, totalStudy, today, streak)
            val unlocked = current >= def.target

            val item = ItemAchievementBinding.inflate(inflater, b.container, false)
            item.tvTitle.text = def.title
            item.tvDesc.text = def.description

            // Status chip
            item.chipStatus.text = if (unlocked) "Unlocked" else "Locked"
            item.chipStatus.isClickable = false
            item.chipStatus.isCheckable = false

            // Progress
            item.tvProgress.text = "${min(current, def.target)} / ${def.target}"
            item.progress.max = def.target
            item.progress.progress = min(current, def.target)

            // Visual hierarchy: dim locked items slightly (HCI: recognition)
            item.root.alpha = if (unlocked) 1.0f else 0.78f

            // Optional hint text for motivation
            item.tvHint.visibility = if (unlocked) View.GONE else View.VISIBLE
            val remaining = (def.target - current).coerceAtLeast(0)
            item.tvHint.text = if (remaining == 0) "" else "Only $remaining more to unlock"

            b.container.addView(item.root)
        }
    }

    private fun currentFor(
        metric: Metric,
        deckCount: Int,
        cardCount: Int,
        totalStudy: Int,
        today: Int,
        streak: Int
    ): Int {
        return when (metric) {
            Metric.DECKS -> deckCount
            Metric.CARDS -> cardCount
            Metric.TOTAL_STUDY -> totalStudy
            Metric.TODAY_STUDY -> today
            Metric.STREAK -> streak
        }
    }
}