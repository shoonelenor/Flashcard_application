package com.example.stardeckapplication.ui.manager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManagerDeckCardsBinding
import com.example.stardeckapplication.databinding.DialogEditCardBinding
import com.example.stardeckapplication.databinding.ItemCardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManagerDeckCardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID = "extra_deck_id"
        const val EXTRA_DECK_TITLE = "extra_deck_title"
        const val EXTRA_OWNER_EMAIL = "extra_owner_email"
        const val EXTRA_ADMIN_EDIT_MODE = "extra_admin_edit_mode"
    }

    private lateinit var b: ActivityManagerDeckCardsBinding

    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var deckId: Long = -1L
    private var adminEditMode: Boolean = false
    private var all: List<StarDeckDbHelper.CardRow> = emptyList()

    private val adapter by lazy {
        CardsAdapter(
            canEdit = adminEditMode,
            onEdit = { showEditDialog(it) },
            onDelete = { confirmDelete(it) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = ActivityManagerDeckCardsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        adminEditMode = intent.getBooleanExtra(EXTRA_ADMIN_EDIT_MODE, false)

        val allowed = when {
            me == null -> false
            adminEditMode -> me.role == DbContract.ROLE_ADMIN
            else -> me.role == DbContract.ROLE_MANAGER
        }

        if (!allowed) {
            finish()
            return
        }

        deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId <= 0L) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_DECK_TITLE) ?: "Deck"

        val ownerEmail = intent.getStringExtra(EXTRA_OWNER_EMAIL).orEmpty()
        b.tvSub.text = if (ownerEmail.isBlank()) "" else "Owner: $ownerEmail"

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged {
            filter(it?.toString().orEmpty())
        }

        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (adminEditMode) {
            menu.add(0, 1001, 0, "Add Card")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1001 -> {
                showCreateDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun reload() {
        val me = session.load() ?: return

        all = if (adminEditMode) {
            db.adminGetCardsForDeck(adminUserId = me.id, deckId = deckId)
        } else {
            db.managerGetCardsForDeck(deckId)
        }

        b.tvCount.text = if (all.size == 1) "1 card" else "${all.size} cards"
        filter(b.etSearch.text?.toString().orEmpty())
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()

        val filtered = if (q.isBlank()) {
            all
        } else {
            all.filter {
                it.front.lowercase().contains(q) || it.back.lowercase().contains(q)
            }
        }

        adapter.submit(filtered)

        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val me = session.load() ?: return

        val d = DialogEditCardBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Card"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error = null

                val front = d.etFront.text?.toString().orEmpty().trim()
                val back = d.etBack.text?.toString().orEmpty().trim()

                if (!validate(front, back, d)) return@setOnClickListener

                val id = db.adminCreateCard(
                    adminUserId = me.id,
                    deckId = deckId,
                    front = front,
                    back = back
                )

                if (id > 0) {
                    Snackbar.make(b.root, "Card created", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not create card", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showEditDialog(card: StarDeckDbHelper.CardRow) {
        val me = session.load() ?: return

        val d = DialogEditCardBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Card"
        d.etFront.setText(card.front)
        d.etBack.setText(card.back)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error = null

                val front = d.etFront.text?.toString().orEmpty().trim()
                val back = d.etBack.text?.toString().orEmpty().trim()

                if (!validate(front, back, d)) return@setOnClickListener

                val rows = db.adminUpdateCard(
                    adminUserId = me.id,
                    deckId = deckId,
                    cardId = card.id,
                    front = front,
                    back = back
                )

                if (rows == 1) {
                    Snackbar.make(b.root, "Card updated", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not update card", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun confirmDelete(card: StarDeckDbHelper.CardRow) {
        val me = session.load() ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete card?")
            .setMessage("This card will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                val rows = db.adminDeleteCard(
                    adminUserId = me.id,
                    deckId = deckId,
                    cardId = card.id
                )
                if (rows == 1) {
                    Snackbar.make(b.root, "Card deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete card", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validate(
        front: String,
        back: String,
        d: com.example.stardeckapplication.databinding.DialogEditCardBinding
    ): Boolean {
        if (front.isBlank()) {
            d.tilFront.error = "Front is required"
            return false
        }
        if (back.isBlank()) {
            d.tilBack.error = "Back is required"
            return false
        }
        if (front.length > 120) {
            d.tilFront.error = "Max 120 characters"
            return false
        }
        if (back.length > 500) {
            d.tilBack.error = "Max 500 characters"
            return false
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class CardsAdapter(
        private val canEdit: Boolean,
        private val onEdit: (StarDeckDbHelper.CardRow) -> Unit,
        private val onDelete: (StarDeckDbHelper.CardRow) -> Unit
    ) : RecyclerView.Adapter<CardsAdapter.VH>() {

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
            return VH(b, expanded, canEdit, onEdit, onDelete) { id ->
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
            private val canEdit: Boolean,
            private val onEdit: (StarDeckDbHelper.CardRow) -> Unit,
            private val onDelete: (StarDeckDbHelper.CardRow) -> Unit,
            private val onToggleId: (Long) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(card: StarDeckDbHelper.CardRow) {
                val isExpanded = expanded.contains(card.id)

                b.tvFront.text = card.front
                b.tvBack.text = card.back
                b.tvHint.text = if (isExpanded) "Tap to hide answer" else "Tap to show answer"

                b.divider.visibility = if (isExpanded) View.VISIBLE else View.GONE
                b.tvBack.visibility = if (isExpanded) View.VISIBLE else View.GONE

                b.btnEdit.visibility = if (canEdit) View.VISIBLE else View.GONE
                b.btnDelete.visibility = if (canEdit) View.VISIBLE else View.GONE

                b.root.setOnClickListener { onToggleId(card.id) }
                b.btnEdit.setOnClickListener { onEdit(card) }
                b.btnDelete.setOnClickListener { onDelete(card) }
            }
        }
    }
}