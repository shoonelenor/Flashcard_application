package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentManagerDecksTabBinding
import com.example.stardeckapplication.databinding.ItemManagerDeckTabBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.manager.ManagerDeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManagerDecksTabFragment : Fragment(R.layout.fragment_manager_decks_tab) {

    private var _b: FragmentManagerDecksTabBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    private var all: List<StarDeckDbHelper.ManagerDeckRow> = emptyList()
    private var statusFilter: String = "all" // all | active | hidden

    private val adapter = DecksAdapter(
        onView = { openDeck(it) },
        onToggle = { toggleDeckStatus(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentManagerDecksTabBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            requireActivity().finish()
            return
        }

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        b.chipAll.setOnClickListener { statusFilter = "all"; applyFilters() }
        b.chipActive.setOnClickListener { statusFilter = DbContract.DECK_ACTIVE; applyFilters() }
        b.chipHidden.setOnClickListener { statusFilter = DbContract.DECK_HIDDEN; applyFilters() }

        safeReload()
    }

    override fun onResume() {
        super.onResume()
        safeReload()
    }

    private fun safeReload() {
        try {
            all = db.managerGetAllDecks()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbError(e)
        } catch (e: Exception) {
            showDbError(e)
        }
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = all

        filtered = when (statusFilter) {
            DbContract.DECK_ACTIVE -> filtered.filter { it.status == DbContract.DECK_ACTIVE }
            DbContract.DECK_HIDDEN -> filtered.filter { it.status == DbContract.DECK_HIDDEN }
            else -> filtered
        }

        if (q.isNotBlank()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                        (it.description ?: "").lowercase().contains(q) ||
                        it.ownerEmail.lowercase().contains(q) ||
                        it.ownerName.lowercase().contains(q)
            }
        }

        adapter.submit(filtered)
        b.tvCount.text = "${filtered.size} deck(s)"

        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE

        if (empty) {
            if (all.isEmpty()) {
                b.tvEmptyTitle.text = "No decks yet"
                b.tvEmptyDesc.text = "Decks will appear here after users create them."
            } else {
                b.tvEmptyTitle.text = "No matches"
                b.tvEmptyDesc.text = "Try changing filters or clearing search."
            }
        }
    }

    private fun openDeck(row: StarDeckDbHelper.ManagerDeckRow) {
        startActivity(
            Intent(requireContext(), ManagerDeckCardsActivity::class.java).apply {
                putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID, row.deckId)
                putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE, row.title)
                putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, row.ownerEmail)
            }
        )
    }

    private fun toggleDeckStatus(row: StarDeckDbHelper.ManagerDeckRow) {
        val newStatus =
            if (row.status == DbContract.DECK_ACTIVE) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE
        val word = if (newStatus == DbContract.DECK_HIDDEN) "Hide" else "Unhide"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$word deck?")
            .setMessage(
                "Deck: ${row.title}\nOwner: ${row.ownerEmail}\n\n" +
                        if (newStatus == DbContract.DECK_HIDDEN) "This will remove it from user view." else "This will make it visible to users."
            )
            .setPositiveButton(word) { _, _ ->
                val rows = db.managerSetDeckStatus(row.deckId, newStatus)
                if (rows == 1) {
                    Snackbar.make(b.root, "Updated", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(b.root, "Could not update", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDbError(e: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Database error")
            .setMessage("Please clear app data / reinstall if you recently changed DB schema.\n\nError: ${e.message}")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private class DecksAdapter(
        val onView: (StarDeckDbHelper.ManagerDeckRow) -> Unit,
        val onToggle: (StarDeckDbHelper.ManagerDeckRow) -> Unit
    ) : RecyclerView.Adapter<DecksAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.ManagerDeckRow>()

        fun submit(newItems: List<StarDeckDbHelper.ManagerDeckRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemManagerDeckTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onView, onToggle)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b: ItemManagerDeckTabBinding,
            val onView: (StarDeckDbHelper.ManagerDeckRow) -> Unit,
            val onToggle: (StarDeckDbHelper.ManagerDeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: StarDeckDbHelper.ManagerDeckRow) {
                b.tvTitle.text = row.title
                b.tvOwner.text = "Owner: ${row.ownerEmail}"
                b.tvMeta.text = "${row.cardCount} card(s) • ${row.status}"
                b.tvDesc.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"

                b.btnView.setOnClickListener { onView(row) }
                b.btnToggle.text = if (row.status == DbContract.DECK_ACTIVE) "Hide" else "Unhide"
                b.btnToggle.setOnClickListener { onToggle(row) }

                // Small joyful indicator
                b.chipStatus.text = if (row.status == DbContract.DECK_ACTIVE) "🟢 Active" else "🙈 Hidden"
            }
        }
    }
}