package com.example.stardeckapplication.ui.manager

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManagerDeckCardsBinding
import com.example.stardeckapplication.databinding.ItemCardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager

class ManagerDeckCardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID = "extra_deck_id"
        const val EXTRA_DECK_TITLE = "extra_deck_title"
        const val EXTRA_OWNER_EMAIL = "extra_owner_email"
    }

    private lateinit var b: ActivityManagerDeckCardsBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var deckId: Long = -1L
    private var all: List<StarDeckDbHelper.CardRow> = emptyList()

    private val adapter = CardsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManagerDeckCardsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            finish()
            return
        }

        deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId <= 0) {
            finish()
            return
        }

        val title = intent.getStringExtra(EXTRA_DECK_TITLE) ?: "Deck"
        val ownerEmail = intent.getStringExtra(EXTRA_OWNER_EMAIL) ?: ""

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = title
        b.tvSub.text = if (ownerEmail.isBlank()) "" else "Owner: $ownerEmail"

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }

        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        all = db.getCardsForDeckAny(deckId)
        b.tvCount.text = if (all.size == 1) "1 card" else "${all.size} cards"
        filter(b.etSearch.text?.toString().orEmpty())
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        val filtered = if (query.isBlank()) all else all.filter {
            it.front.lowercase().contains(query) || it.back.lowercase().contains(query)
        }

        adapter.submit(filtered)

        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private class CardsAdapter : RecyclerView.Adapter<CardsAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.CardRow>()
        private val expanded = mutableSetOf<Long>()

        fun submit(newItems: List<StarDeckDbHelper.CardRow>) {
            items.clear()
            items.addAll(newItems)
            expanded.retainAll(newItems.map { it.id }.toSet())
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, expanded) { id ->
                if (!expanded.add(id)) expanded.remove(id)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b: ItemCardBinding,
            private val expanded: Set<Long>,
            private val onToggle: (Long) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(card: StarDeckDbHelper.CardRow) {
                val isExpanded = expanded.contains(card.id)
                b.tvFront.text = card.front
                b.tvBack.text = card.back
                b.tvHint.text = if (isExpanded) "Tap to hide answer" else "Tap to show answer"
                b.divider.visibility = if (isExpanded) View.VISIBLE else View.GONE
                b.tvBack.visibility = if (isExpanded) View.VISIBLE else View.GONE

                // read-only: hide action buttons
                b.btnEdit.visibility = View.GONE
                b.btnDelete.visibility = View.GONE

                b.root.setOnClickListener { onToggle(card.id) }
            }
        }
    }
}