package com.example.stardeckapplication.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ItemAchievementBinding
import com.example.stardeckapplication.db.AchievementDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlin.math.min

class AchievementsActivity : AppCompatActivity() {

    private val session by lazy { SessionManager(this) }
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val achievementDao by lazy { AchievementDao(dbHelper) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Achievements"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        loadAchievements(showUnlockToast = true)
    }

    override fun onResume() {
        super.onResume()
        loadAchievements(showUnlockToast = false)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadAchievements(showUnlockToast: Boolean) {
        val me = session.load() ?: return

        achievementDao.ensureDefaultsInserted()
        val newlyUnlocked = achievementDao.syncUserAchievements(me.id)
        val rows = achievementDao.getUserAchievements(me.id)

        val tvSummary: TextView = findViewById(R.id.tvSummary)
        val container: LinearLayout = findViewById(R.id.container)

        container.removeAllViews()

        if (rows.isEmpty()) {
            tvSummary.text = "No achievements configured yet."
            val emptyText = TextView(this).apply {
                text = "Admin can create achievements from Master Data."
                textSize = 16f
            }
            container.addView(emptyText)
            return
        }

        val unlockedCount = rows.count { it.isUnlocked }
        tvSummary.text = "$unlockedCount / ${rows.size} unlocked"

        val inflater = LayoutInflater.from(this)
        rows.forEach { row ->
            val item = ItemAchievementBinding.inflate(inflater, container, false)
            item.tvTitle.text = row.title
            item.tvDesc.text = buildString {
                append(row.description ?: "No description")
                append("\nMetric: ${row.metricLabel}")
            }

            item.chipStatus.text = if (row.isUnlocked) "Unlocked" else "Locked"
            item.chipStatus.isClickable = false
            item.chipStatus.isCheckable = false

            val progressValue = min(row.currentValue, row.targetValue)
            item.tvProgress.text = "$progressValue / ${row.targetValue}"
            item.progress.max = row.targetValue
            item.progress.progress = progressValue
            item.root.alpha = if (row.isUnlocked) 1.0f else 0.78f

            item.tvHint.visibility = View.VISIBLE
            if (row.isUnlocked) {
                item.tvHint.text = "Unlocked"
            } else {
                val remaining = (row.targetValue - row.currentValue).coerceAtLeast(0)
                item.tvHint.text = "Only $remaining more to unlock"
            }

            container.addView(item.root)
        }

        if (showUnlockToast && newlyUnlocked > 0) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "$newlyUnlocked achievement(s) unlocked!",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}