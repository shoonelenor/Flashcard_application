package com.example.stardeckapplication.ui.home

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.DialogEditDeckBinding
import com.example.stardeckapplication.databinding.FragmentUserDecksBinding
import com.example.stardeckapplication.databinding.ItemDeckBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class UserDecksFragment : Fragment(R.layout.fragment_user_decks) {

    private var _b: FragmentUserDecksBinding? = null
    private val b get() = _b!!

    private val db by lazy { StarDeckDbHelper(requireContext()) }
    private val session by lazy { SessionManager(requireContext()) }

    private var all: List<StarDeckDbHelper.DeckRow> = emptyList()
    private var isPremiumUser: Boolean = false

    private val adapter = DecksAdapter(
        onOpen = { openDeck(it) },
        onEdit = { showEditDialog(it) },
        onDelete = { confirmDelete(it) },
        isPremiumUser = { isPremiumUser },
        onLocked = { deck ->
            // Go to premium screen and remember which deck user wanted
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deck.id)
            )
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserDecksBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.fabAdd.setOnClickListener { showCreateDialog() }
        b.btnCreateFirst.setOnClickListener { showCreateDialog() }

        b.etSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun openDeck(deck: StarDeckDbHelper.DeckRow) {
        val me = session.load() ?: return

        // Premium lock (demo)
        if (deck.isPremium && !isPremiumUser) {
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deck.id)
            )
            return
        }

        startActivity(
            Intent(requireContext(), DeckCardsActivity::class.java)
                .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deck.id)
        )
    }

    private fun reload() {
        val me = session.load() ?: return
        isPremiumUser = db.isUserPremium(me.id)

        all = db.getDecksForOwner(me.id)
        filter(b.etSearch.text?.toString().orEmpty())
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) all else all.filter {
            it.title.lowercase().contains(q) || (it.description ?: "").lowercase().contains(q)
        }

        adapter.submit(filtered)

        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val me = session.load() ?: return
        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Deck"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilTitle.error = null
                val title = d.etTitle.text?.toString().orEmpty().trim()
                val desc = d.etDescription.text?.toString()

                when {
                    title.isBlank() -> { d.tilTitle.error = "Title is required"; return@setOnClickListener }
                    title.length > 40 -> { d.tilTitle.error = "Max 40 characters"; return@setOnClickListener }
                }

                db.createDeck(me.id, title, desc)
                Snackbar.make(b.root, "Deck created", Snackbar.LENGTH_SHORT).show()
                reload()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEditDialog(deck: StarDeckDbHelper.DeckRow) {
        val me = session.load() ?: return

        // Do not allow editing premium demo decks (keeps demo clean)
        if (deck.isPremium) {
            Snackbar.make(b.root, "Premium decks can’t be edited (demo).", Snackbar.LENGTH_SHORT).show()
            return
        }

        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Deck"
        d.etTitle.setText(deck.title)
        d.etDescription.setText(deck.description.orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilTitle.error = null
                val title = d.etTitle.text?.toString().orEmpty().trim()
                val desc = d.etDescription.text?.toString()

                when {
                    title.isBlank() -> { d.tilTitle.error = "Title is required"; return@setOnClickListener }
                    title.length > 40 -> { d.tilTitle.error = "Max 40 characters"; return@setOnClickListener }
                }

                val rows = db.updateDeck(me.id, deck.id, title, desc)
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

    private fun confirmDelete(deck: StarDeckDbHelper.DeckRow) {
        val me = session.load() ?: return

        if (deck.isPremium) {
            Snackbar.make(b.root, "Premium demo deck can’t be deleted.", Snackbar.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete deck?")
            .setMessage("“${deck.title}” and its cards will be deleted. You can’t undo this.")
            .setPositiveButton("Delete") { _, _ ->
                val rows = db.deleteDeck(me.id, deck.id)
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
        val onOpen: (StarDeckDbHelper.DeckRow) -> Unit,
        val onEdit: (StarDeckDbHelper.DeckRow) -> Unit,
        val onDelete: (StarDeckDbHelper.DeckRow) -> Unit,
        val isPremiumUser: () -> Boolean,
        val onLocked: (StarDeckDbHelper.DeckRow) -> Unit
    ) : RecyclerView.Adapter<DecksAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.DeckRow>()

        fun submit(newItems: List<StarDeckDbHelper.DeckRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemDeckBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onOpen, onEdit, onDelete, isPremiumUser, onLocked)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        class VH(
            private val b: ItemDeckBinding,
            val onOpen: (StarDeckDbHelper.DeckRow) -> Unit,
            val onEdit: (StarDeckDbHelper.DeckRow) -> Unit,
            val onDelete: (StarDeckDbHelper.DeckRow) -> Unit,
            val isPremiumUser: () -> Boolean,
            val onLocked: (StarDeckDbHelper.DeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(deck: StarDeckDbHelper.DeckRow) {
                b.tvTitle.text = deck.title
                b.tvDesc.text = deck.description?.takeIf { it.isNotBlank() } ?: "No description"

                val premiumUser = isPremiumUser()

                if (deck.isPremium) {
                    b.chipPremium.visibility = View.VISIBLE
                    b.chipPremium.text = if (premiumUser) "⭐ Premium" else "🔒 Premium"

                    // Hide edit/delete for premium demo deck
                    b.btnEdit.visibility = View.GONE
                    b.btnDelete.visibility = View.GONE

                    b.root.setOnClickListener {
                        if (premiumUser) onOpen(deck) else onLocked(deck)
                    }
                } else {
                    b.chipPremium.visibility = View.GONE
                    b.btnEdit.visibility = View.VISIBLE
                    b.btnDelete.visibility = View.VISIBLE

                    b.root.setOnClickListener { onOpen(deck) }
                    b.btnEdit.setOnClickListener { onEdit(deck) }
                    b.btnDelete.setOnClickListener { onDelete(deck) }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun setDialogBusy(dialog: AlertDialog, busy: Boolean) {
        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = !busy
        btn.alpha = if (busy) 0.6f else 1f
    }
}

