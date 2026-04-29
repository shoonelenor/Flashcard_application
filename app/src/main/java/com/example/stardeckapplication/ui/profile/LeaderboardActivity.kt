package com.example.stardeckapplication.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityLeaderboardBinding
import com.example.stardeckapplication.databinding.ItemLeaderboardUserBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StatsDao
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityLeaderboardBinding

    private val session  by lazy { SessionManager(this) }
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val statsDao by lazy { StatsDao(dbHelper) }

    private val adapter = LeaderboardAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Leaderboard"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter       = adapter

        loadData()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadData() {
        val rows: List<StatsDao.LeaderboardRow> = runCatching { statsDao.getLocalLeaderboard() }
            .getOrElse {
                Snackbar.make(b.root, "Could not load leaderboard.", Snackbar.LENGTH_LONG).show()
                emptyList()
            }

        if (rows.isEmpty()) {
            b.groupEmpty.visibility = View.VISIBLE
            b.recycler.visibility   = View.GONE
        } else {
            b.groupEmpty.visibility = View.GONE
            b.recycler.visibility   = View.VISIBLE
            adapter.submitList(rows)
        }
    }

    // ══════════════════════════════════════════════════════
    //  ADAPTER
    // ══════════════════════════════════════════════════════

    private class LeaderboardAdapter :
        ListAdapter<StatsDao.LeaderboardRow, LeaderboardAdapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemLeaderboardUserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) =
            holder.bind(position, getItem(position))

        class VH(private val b: ItemLeaderboardUserBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(position: Int, row: StatsDao.LeaderboardRow) {
                b.tvRank.text  = (position + 1).toString()
                b.tvName.text  = row.name
                // tvEmail now shows a friendly rank label instead of private email
                b.tvEmail.text = when (position) {
                    0 -> "🥇 Top Studier"
                    1 -> "🥈 2nd Place"
                    2 -> "🥉 3rd Place"
                    else -> "Rank #${position + 1}"
                }
                val streakText = if (row.streakDays == 1) "1 day" else "${row.streakDays} days"
                b.tvStats.text = "Total: ${row.totalStudy} • Streak: $streakText"
            }
        }

        private object Diff : DiffUtil.ItemCallback<StatsDao.LeaderboardRow>() {
            override fun areItemsTheSame(
                oldItem: StatsDao.LeaderboardRow,
                newItem: StatsDao.LeaderboardRow
            ): Boolean = oldItem.userId == newItem.userId

            override fun areContentsTheSame(
                oldItem: StatsDao.LeaderboardRow,
                newItem: StatsDao.LeaderboardRow
            ): Boolean = oldItem == newItem
        }
    }
}
