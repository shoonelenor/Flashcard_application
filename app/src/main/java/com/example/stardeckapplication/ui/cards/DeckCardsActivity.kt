package com.example.stardeckapplication.ui.cards

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityDeckCardsBinding
import com.example.stardeckapplication.databinding.DialogEditCardBinding
import com.example.stardeckapplication.databinding.ItemCardBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.study.StudyActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class DeckCardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID = "extra_deck_id"
    }

    private lateinit var b: ActivityDeckCardsBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var deckId: Long = -1L
    private var all: List<StarDeckDbHelper.CardRow> = emptyList()

    private val adapter = CardsAdapter(
        onEdit = { showEditDialog(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDeckCardsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId <= 0) {
            finish()
            return
        }

        val title = db.getDeckTitleForOwner(me.id, deckId)
        if (title == null) {
            Snackbar.make(b.root, "Deck not found (or hidden)", Snackbar.LENGTH_LONG).show()
            finish()
            return
        }
        supportActionBar?.title = title

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.fabAdd.setOnClickListener { showCreateDialog() }
        b.btnCreateFirst.setOnClickListener { showCreateDialog() }

        b.btnStudy.setOnClickListener {
            startActivity(Intent(this, StudyActivity::class.java).putExtra(StudyActivity.EXTRA_DECK_ID, deckId))
        }

        b.etSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }

        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_deck_cards, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_report_deck -> { showReportDeckDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showReportDeckDialog() {
        val me = session.load() ?: return

        val til = TextInputLayout(this).apply {
            hint = "Reason (e.g., offensive content, wrong info)"
        }
        val et = TextInputEditText(this).apply {
            minLines = 2
            maxLines = 4
        }
        til.addView(et)

        MaterialAlertDialogBuilder(this)
            .setTitle("Report deck")
            .setMessage("Describe the issue. A Manager will review this report.")
            .setView(til)
            .setPositiveButton("Submit") { _, _ ->
                val reason = et.text?.toString().orEmpty().trim()
                if (reason.length < 3) {
                    Snackbar.make(b.root, "Please enter a short reason.", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val id = db.createDeckReport(me.id, deckId, reason, null)
                if (id > 0) {
                    Snackbar.make(b.root, "Report submitted", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(b.root, "Could not submit report", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reload() {
        val me = session.load() ?: return
        all = db.getCardsForDeck(me.id, deckId)

        val n = all.size
        b.tvCount.text = if (n == 1) "1 card" else "$n cards"
        b.btnStudy.isEnabled = n > 0

        filter(b.etSearch.text?.toString().orEmpty())
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) all else all.filter {
            it.front.lowercase().contains(q) || it.back.lowercase().contains(q)
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
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error = null

                val front = d.etFront.text?.toString().orEmpty()
                val back = d.etBack.text?.toString().orEmpty()

                if (!validate(front, back, d)) return@setOnClickListener

                val id = db.createCard(me.id, deckId, front, back)
                if (id > 0) {
                    Snackbar.make(b.root, "Card created", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not create", Snackbar.LENGTH_LONG).show()
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
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error = null

                val front = d.etFront.text?.toString().orEmpty()
                val back = d.etBack.text?.toString().orEmpty()

                if (!validate(front, back, d)) return@setOnClickListener

                val rows = db.updateCard(me.id, deckId, card.id, front, back)
                if (rows == 1) {
                    Snackbar.make(b.root, "Saved", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not update", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(card: StarDeckDbHelper.CardRow) {
        val me = session.load() ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete card?")
            .setMessage("This card will be deleted. You can’t undo this.")
            .setPositiveButton("Delete") { _, _ ->
                val rows = db.deleteCard(me.id, deckId, card.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Card deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validate(front: String, back: String, d: DialogEditCardBinding): Boolean {
        val f = front.trim()
        val bck = back.trim()

        if (f.isBlank()) { d.tilFront.error = "Front is required"; return false }
        if (bck.isBlank()) { d.tilBack.error = "Back is required"; return false }
        if (f.length > 120) { d.tilFront.error = "Max 120 characters"; return false }
        if (bck.length > 500) { d.tilBack.error = "Max 500 characters"; return false }
        return true
    }

    private class CardsAdapter(
        val onEdit: (StarDeckDbHelper.CardRow) -> Unit,
        val onDelete: (StarDeckDbHelper.CardRow) -> Unit
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
            return VH(b, expanded, onEdit, onDelete) { id ->
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
            val onEdit: (StarDeckDbHelper.CardRow) -> Unit,
            val onDelete: (StarDeckDbHelper.CardRow) -> Unit,
            val onToggleId: (Long) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(card: StarDeckDbHelper.CardRow) {
                val isExpanded = expanded.contains(card.id)
                b.tvFront.text = card.front
                b.tvBack.text = card.back
                b.tvHint.text = if (isExpanded) "Tap to hide answer" else "Tap to show answer"
                b.divider.visibility = if (isExpanded) View.VISIBLE else View.GONE
                b.tvBack.visibility = if (isExpanded) View.VISIBLE else View.GONE

                b.root.setOnClickListener { onToggleId(card.id) }
                b.btnEdit.setOnClickListener { onEdit(card) }
                b.btnDelete.setOnClickListener { onDelete(card) }
            }
        }
    }

    private fun setDialogBusy(dialog: AlertDialog, busy: Boolean) {
        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = !busy
        btn.alpha = if (busy) 0.6f else 1f
    }
}