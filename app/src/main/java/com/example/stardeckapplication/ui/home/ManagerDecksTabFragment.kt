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

    private var _binding: FragmentManagerDecksTabBinding? = null
    private val binding get() = _binding!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    private var allDecks: List<StarDeckDbHelper.ManagerDeckRow> = emptyList()
    private var statusFilter: String = "all" // all | active | hidden

    private val decksAdapter = DecksAdapter(
        onView = { openDeck(it) },
        onToggle = { toggleDeckStatus(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentManagerDecksTabBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            requireActivity().finish()
            return
        }

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = decksAdapter

        binding.etSearch.doAfterTextChanged { applyFilters() }

        binding.chipAll.setOnClickListener {
            statusFilter = "all"
            applyFilters()
        }

        binding.chipActive.setOnClickListener {
            statusFilter = DbContract.DECK_ACTIVE
            applyFilters()
        }

        binding.chipHidden.setOnClickListener {
            statusFilter = DbContract.DECK_HIDDEN
            applyFilters()
        }

        safeReload()
    }

    override fun onResume() {
        super.onResume()
        safeReload()
    }

    private fun safeReload() {
        try {
            allDecks = db.managerGetAllDecks()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbError(e)
        } catch (e: Exception) {
            showDbError(e)
        }
    }

    private fun applyFilters() {
        val q = binding.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = allDecks

        filtered = when (statusFilter) {
            DbContract.DECK_ACTIVE -> filtered.filter { it.status == DbContract.DECK_ACTIVE }
            DbContract.DECK_HIDDEN -> filtered.filter { it.status == DbContract.DECK_HIDDEN }
            else -> filtered
        }

        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.title.lowercase().contains(q) ||
                        (row.description ?: "").lowercase().contains(q) ||
                        row.ownerEmail.lowercase().contains(q) ||
                        row.ownerName.lowercase().contains(q)
            }
        }

        decksAdapter.submit(filtered)

        binding.tvCount.text = "${filtered.size} deck(s)"

        val isEmpty = filtered.isEmpty()
        binding.groupEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (isEmpty) {
            if (allDecks.isEmpty()) {
                binding.tvEmptyTitle.text = "No decks yet"
                binding.tvEmptyDesc.text = "Decks will appear here after users create them."
            } else {
                binding.tvEmptyTitle.text = "No matches"
                binding.tvEmptyDesc.text = "Try changing filters or clearing search."
            }
        }
    }

    private fun openDeck(row: StarDeckDbHelper.ManagerDeckRow) {
        val intent = Intent(requireContext(), ManagerDeckCardsActivity::class.java)
        intent.putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID, row.deckId)
        intent.putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE, row.title)
        intent.putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, row.ownerEmail)
        startActivity(intent)
    }

    private fun toggleDeckStatus(row: StarDeckDbHelper.ManagerDeckRow) {
        val newStatus =
            if (row.status == DbContract.DECK_ACTIVE) DbContract.DECK_HIDDEN
            else DbContract.DECK_ACTIVE

        val actionWord = if (newStatus == DbContract.DECK_HIDDEN) "Hide" else "Unhide"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$actionWord deck?")
            .setMessage(
                "Deck: ${row.title}\nOwner: ${row.ownerEmail}\n\n" +
                        if (newStatus == DbContract.DECK_HIDDEN) {
                            "This will remove it from user view."
                        } else {
                            "This will make it visible to users."
                        }
            )
            .setPositiveButton(actionWord) { _, _ ->
                val rows = db.managerSetDeckStatus(row.deckId, newStatus)
                if (rows == 1) {
                    Snackbar.make(binding.root, "Updated", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not update", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDbError(e: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Database error")
            .setMessage(
                "Please clear app data / reinstall if you recently changed DB schema.\n\nError: ${e.message}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class DecksAdapter(
        private val onView: (StarDeckDbHelper.ManagerDeckRow) -> Unit,
        private val onToggle: (StarDeckDbHelper.ManagerDeckRow) -> Unit
    ) : RecyclerView.Adapter<DecksAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.ManagerDeckRow>()

        fun submit(newItems: List<StarDeckDbHelper.ManagerDeckRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemManagerDeckTabBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onView, onToggle)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val binding: ItemManagerDeckTabBinding,
            private val onView: (StarDeckDbHelper.ManagerDeckRow) -> Unit,
            private val onToggle: (StarDeckDbHelper.ManagerDeckRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: StarDeckDbHelper.ManagerDeckRow) {
                binding.tvTitle.text = row.title
                binding.tvOwner.text = "Owner: ${row.ownerEmail}"
                binding.tvMeta.text = "${row.cardCount} card(s) • ${row.status}"
                binding.tvDesc.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"

                binding.btnView.setOnClickListener { onView(row) }

                binding.btnToggle.text =
                    if (row.status == DbContract.DECK_ACTIVE) "Hide" else "Unhide"

                binding.btnToggle.setOnClickListener { onToggle(row) }

                binding.chipStatus.text =
                    if (row.status == DbContract.DECK_ACTIVE) "Active" else "Hidden"
            }
        }
    }
}