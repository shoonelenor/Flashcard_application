package com.example.stardeckapplication.ui.decks

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityMyDecksBinding
import com.example.stardeckapplication.databinding.DialogEditDeckBinding
import com.example.stardeckapplication.databinding.ItemDeckBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.DeckDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MyDecksActivity : AppCompatActivity() {

    private lateinit var b: ActivityMyDecksBinding

    // DeckDao handles all deck operations and exposes DeckRow
    private val deckDao by lazy { DeckDao(StarDeckDbHelper(this)) }
    private val session by lazy { SessionManager(this) }

    private var all: List<DeckDao.DeckRow> = emptyList()
    private val adapter = DecksAdapter(
        onOpen   = { openDeck(it) },
        onEdit   = { showEditDialog(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMyDecksBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Decks"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.fabAdd.setOnClickListener         { showCreateDialog() }
        b.btnCreateFirst.setOnClickListener { showCreateDialog() }

        b.etSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }

        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun openDeck(deck: DeckDao.DeckRow) {
        startActivity(
            Intent(this, DeckCardsActivity::class.java)
                .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deck.id)
        )
    }

    private fun reload() {
        val me = session.load() ?: return
        // DeckDao needs: fun getDecksForOwner(ownerUserId: Long): List<DeckRow>
        all = deckDao.getDecksForOwner(me.id)
        filter(b.etSearch.text?.toString().orEmpty())
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) all else all.filter {
            it.title.lowercase().contains(q) ||
                    (it.description ?: "").lowercase().contains(q)
        }
        adapter.submit(filtered)
        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility   = if (empty) View.GONE    else View.VISIBLE
    }

    private fun showCreateDialog() {
        val me = session.load() ?: return
        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Deck"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error = null
                    val title = d.etTitle.text?.toString().orEmpty()
                    val desc  = d.etDescription.text?.toString()
                    val t     = title.trim()
                    when {
                        t.isBlank()   -> { d.tilTitle.error = "Title is required"; return@setOnClickListener }
                        t.length > 40 -> { d.tilTitle.error = "Max 40 characters";  return@setOnClickListener }
                    }
                    // DeckDao needs: fun createDeck(ownerUserId: Long, title: String, description: String?): Long
                    deckDao.createDeck(me.id, t, desc)
                    Snackbar.make(b.root, "Deck created", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    private fun showEditDialog(deck: DeckDao.DeckRow) {
        val me = session.load() ?: return
        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Deck"
        d.etTitle.setText(deck.title)
        d.etDescription.setText(deck.description.orEmpty())

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error = null
                    val title = d.etTitle.text?.toString().orEmpty()
                    val desc  = d.etDescription.text?.toString()
                    val t     = title.trim()
                    when {
                        t.isBlank()   -> { d.tilTitle.error = "Title is required"; return@setOnClickListener }
                        t.length > 40 -> { d.tilTitle.error = "Max 40 characters";  return@setOnClickListener }
                    }
                    // DeckDao needs: fun updateDeck(ownerUserId: Long, deckId: Long, title: String, description: String?): Int
                    val rows = deckDao.updateDeck(me.id, deck.id, t, desc)
                    if (rows == 1) {
                        Snackbar.make(b.root, "Saved", Snackbar.LENGTH_SHORT).show()
                        reload()
                    } else {
                        Snackbar.make(b.root, "Could not update", Snackbar.LENGTH_LONG).show()
                    }
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    private fun confirmDelete(deck: DeckDao.DeckRow) {
        val me = session.load() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete deck?")
            .setMessage("“${deck.title}” and its cards will be deleted. You can’t undo this.")
            .setPositiveButton("Delete") { _, _ ->
                // DeckDao needs: fun deleteDeck(ownerUserId: Long, deckId: Long): Int
                val rows = deckDao.deleteDeck(me.id, deck.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Deck deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class DecksAdapter(
        val onOpen   : (DeckDao.DeckRow) -> Unit,
        val onEdit   : (DeckDao.DeckRow) -> Unit,
        val onDelete : (DeckDao.DeckRow) -> Unit
    ) : RecyclerView.Adapter<DecksAdapter.VH>() {

        private val items = mutableListOf<DeckDao.DeckRow>()

        fun submit(newItems: List<DeckDao.DeckRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemDeckBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onOpen, onEdit, onDelete)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b  : ItemDeckBinding,
            val onOpen     : (DeckDao.DeckRow) -> Unit,
            val onEdit     : (DeckDao.DeckRow) -> Unit,
            val onDelete   : (DeckDao.DeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(deck: DeckDao.DeckRow) {
                b.tvTitle.text = deck.title
                b.tvDesc.text  = deck.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.root.setOnClickListener      { onOpen(deck)   }
                b.btnEdit.setOnClickListener   { onEdit(deck)   }
                b.btnDelete.setOnClickListener { onDelete(deck) }
            }
        }
    }
}