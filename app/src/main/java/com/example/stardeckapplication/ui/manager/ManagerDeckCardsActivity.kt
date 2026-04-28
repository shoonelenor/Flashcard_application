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
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.DeckDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManagerDeckCardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID       = "extra_deck_id"
        const val EXTRA_DECK_TITLE    = "extra_deck_title"
        const val EXTRA_OWNER_EMAIL   = "extra_owner_email"
        const val EXTRA_ADMIN_EDIT_MODE = "extra_admin_edit_mode"

        private const val MENU_ADD_CARD = 1001
    }

    private enum class ScreenMode { MANAGER_REVIEW, ADMIN_EDIT }

    private lateinit var b: ActivityManagerDeckCardsBinding

    private val dbHelper  by lazy { StarDeckDbHelper(this) }
    private val deckDao   by lazy { DeckDao(dbHelper) }
    private val cardDao   by lazy { CardDao(dbHelper) }
    private val session   by lazy { SessionManager(this) }

    private var deckId     : Long       = -1L
    private var screenMode : ScreenMode = ScreenMode.MANAGER_REVIEW

    private var allCards   : List<DeckDao.CardRow> = emptyList()

    private val adapter by lazy {
        CardsAdapter(
            canEdit  = isAdminEditMode(),
            onEdit   = { showEditDialog(it) },
            onDelete = { confirmDelete(it) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManagerDeckCardsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null) { finish(); return }

        screenMode = resolveScreenMode(me.role) ?: run { finish(); return }

        deckId = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        if (deckId <= 0L) { finish(); return }

        setupToolbar()
        setupList()
        setupSearch()
        reloadCards()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isAdminEditMode()) {
            menu.add(0, MENU_ADD_CARD, 0, "Add Card")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            MENU_ADD_CARD     -> { showCreateDialog(); true }
            else              -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun resolveScreenMode(role: String): ScreenMode? {
        val wantsAdminEdit = intent.getBooleanExtra(EXTRA_ADMIN_EDIT_MODE, false)
        return when {
            wantsAdminEdit  && role == DbContract.ROLE_ADMIN   -> ScreenMode.ADMIN_EDIT
            !wantsAdminEdit && role == DbContract.ROLE_MANAGER -> ScreenMode.MANAGER_REVIEW
            else -> null
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_DECK_TITLE) ?: "Deck"

        val ownerEmail = intent.getStringExtra(EXTRA_OWNER_EMAIL).orEmpty()
        val modeText   = if (isAdminEditMode()) "Edit mode" else "Review mode"

        b.tvSub.text = buildString {
            if (ownerEmail.isNotBlank()) { append("Owner: "); append(ownerEmail); append(" • ") }
            append(modeText)
        }
    }

    private fun setupList() {
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter       = adapter
    }

    private fun setupSearch() {
        b.etSearch.doAfterTextChanged { filterCards(it?.toString().orEmpty()) }
    }

    private fun reloadCards() {
        val me = session.load() ?: return

        allCards = if (isAdminEditMode()) {
            deckDao.adminGetCardsForDeck(adminUserId = me.id, deckId = deckId)
        } else {
            cardDao.managerGetCardsForDeck(deckId).map { c ->
                DeckDao.CardRow(
                    id        = c.id,
                    front     = c.front,
                    back      = c.back,
                    createdAt = c.createdAt
                )
            }
        }

        b.tvCount.text = if (allCards.size == 1) "1 card" else "${allCards.size} cards"
        filterCards(b.etSearch.text?.toString().orEmpty())
    }

    private fun filterCards(query: String) {
        val q = query.trim().lowercase()

        val filtered: List<DeckDao.CardRow> = if (q.isBlank()) {
            allCards
        } else {
            allCards.filter { card ->
                card.front.lowercase().contains(q) || card.back.lowercase().contains(q)
            }
        }

        adapter.submit(filtered)

        val isEmpty = filtered.isEmpty()
        b.groupEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        b.recycler.visibility   = if (isEmpty) View.GONE    else View.VISIBLE
    }

    private fun showCreateDialog() {
        if (!isAdminEditMode()) return
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
                d.tilBack.error  = null

                // etFrontEdit is the TextInputEditText inside tilFront
                val front = d.etFrontEdit.text?.toString().orEmpty().trim()
                val back  = d.etBackEdit.text?.toString().orEmpty().trim()

                if (!validate(front, back, d)) return@setOnClickListener

                val id = deckDao.adminCreateCard(
                    adminUserId = me.id,
                    deckId      = deckId,
                    front       = front,
                    back        = back
                )

                if (id > 0) {
                    Snackbar.make(b.root, "Card created", Snackbar.LENGTH_SHORT).show()
                    reloadCards()
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not create card", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun showEditDialog(card: DeckDao.CardRow) {
        if (!isAdminEditMode()) return
        val me = session.load() ?: return

        val d = DialogEditCardBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Card"
        // etFrontEdit / etBackEdit are the TextInputEditText views inside tilFront / tilBack
        d.etFrontEdit.setText(card.front)
        d.etBackEdit.setText(card.back)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error  = null

                val front = d.etFrontEdit.text?.toString().orEmpty().trim()
                val back  = d.etBackEdit.text?.toString().orEmpty().trim()

                if (!validate(front, back, d)) return@setOnClickListener

                val rows = deckDao.adminUpdateCard(
                    adminUserId = me.id,
                    deckId      = deckId,
                    cardId      = card.id,
                    front       = front,
                    back        = back
                )

                if (rows == 1) {
                    Snackbar.make(b.root, "Card updated", Snackbar.LENGTH_SHORT).show()
                    reloadCards()
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not update card", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(card: DeckDao.CardRow) {
        if (!isAdminEditMode()) return
        val me = session.load() ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete card?")
            .setMessage("This card will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                val rows = deckDao.adminDeleteCard(
                    adminUserId = me.id,
                    deckId      = deckId,
                    cardId      = card.id
                )
                if (rows == 1) {
                    Snackbar.make(b.root, "Card deleted", Snackbar.LENGTH_SHORT).show()
                    reloadCards()
                } else {
                    Snackbar.make(b.root, "Could not delete card", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validate(front: String, back: String, d: DialogEditCardBinding): Boolean {
        if (front.isBlank())    { d.tilFront.error = "Front is required";  return false }
        if (back.isBlank())     { d.tilBack.error  = "Back is required";   return false }
        if (front.length > 120) { d.tilFront.error = "Max 120 characters"; return false }
        if (back.length > 500)  { d.tilBack.error  = "Max 500 characters"; return false }
        return true
    }

    private fun isAdminEditMode(): Boolean = screenMode == ScreenMode.ADMIN_EDIT

    // ══════════════════════════════════════════════════════════════════════
    //  ADAPTER
    // ══════════════════════════════════════════════════════════════════════

    private class CardsAdapter(
        private val canEdit  : Boolean,
        private val onEdit   : (DeckDao.CardRow) -> Unit,
        private val onDelete : (DeckDao.CardRow) -> Unit
    ) : RecyclerView.Adapter<CardsAdapter.VH>() {

        private val items       = mutableListOf<DeckDao.CardRow>()
        private val expandedIds = mutableSetOf<Long>()

        fun submit(newItems: List<DeckDao.CardRow>) {
            items.clear()
            items.addAll(newItems)
            expandedIds.retainAll(newItems.map { it.id }.toSet())
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(
                b           = binding,
                expandedIds = expandedIds,
                canEdit     = canEdit,
                onEdit      = onEdit,
                onDelete    = onDelete,
                onToggleId  = { id ->
                    if (!expandedIds.add(id)) expandedIds.remove(id)
                    notifyDataSetChanged()
                }
            )
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        class VH(
            private val b           : ItemCardBinding,
            private val expandedIds : Set<Long>,
            private val canEdit     : Boolean,
            private val onEdit      : (DeckDao.CardRow) -> Unit,
            private val onDelete    : (DeckDao.CardRow) -> Unit,
            private val onToggleId  : (Long) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(card: DeckDao.CardRow) {
                val isExpanded = expandedIds.contains(card.id)

                b.tvFront.text = card.front
                b.tvBack.text  = card.back
                b.tvHint.text  = if (isExpanded) "Tap to hide answer" else "Tap to show answer"

                b.divider.visibility   = if (isExpanded) View.VISIBLE else View.GONE
                b.tvBack.visibility    = if (isExpanded) View.VISIBLE else View.GONE
                b.btnEdit.visibility   = if (canEdit) View.VISIBLE else View.GONE
                b.btnDelete.visibility = if (canEdit) View.VISIBLE else View.GONE

                b.root.setOnClickListener      { onToggleId(card.id) }
                b.btnEdit.setOnClickListener   { onEdit(card) }
                b.btnDelete.setOnClickListener { onDelete(card) }
            }
        }
    }
}
