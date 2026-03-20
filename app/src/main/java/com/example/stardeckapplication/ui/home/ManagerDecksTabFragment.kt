package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
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
    private var statusFilter: String = FILTER_ALL
    private var searchQuery: String = ""

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

        restoreUiState(savedInstanceState)
        setupRecycler()
        setupSearch()
        setupChips()
        applySavedUiState()
        safeReload()
    }

    override fun onResume() {
        super.onResume()
        safeReload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_STATUS_FILTER, statusFilter)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        statusFilter = savedInstanceState?.getString(KEY_STATUS_FILTER) ?: FILTER_ALL
        searchQuery = savedInstanceState?.getString(KEY_SEARCH_QUERY).orEmpty()
    }

    private fun setupRecycler() {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = decksAdapter
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            applyFilters()
        }
    }

    private fun setupChips() {
        binding.chipAll.setOnClickListener {
            setStatusFilter(FILTER_ALL)
        }

        binding.chipActive.setOnClickListener {
            setStatusFilter(DbContract.DECK_ACTIVE)
        }

        binding.chipHidden.setOnClickListener {
            setStatusFilter(DbContract.DECK_HIDDEN)
        }
    }

    private fun applySavedUiState() {
        binding.etSearch.setText(searchQuery)
        updateStatusChips()
    }

    private fun setStatusFilter(value: String) {
        statusFilter = value
        updateStatusChips()
        applyFilters()
    }

    private fun updateStatusChips() {
        binding.chipAll.isChecked = statusFilter == FILTER_ALL
        binding.chipActive.isChecked = statusFilter == DbContract.DECK_ACTIVE
        binding.chipHidden.isChecked = statusFilter == DbContract.DECK_HIDDEN
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
        val q = searchQuery.trim().lowercase()

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

        decksAdapter.submitList(filtered.toList())

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
    ) : ListAdapter<StarDeckDbHelper.ManagerDeckRow, DecksAdapter.VH>(DeckDiffCallback()) {

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = getItem(position).deckId

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemManagerDeckTabBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onView, onToggle)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
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

    private class DeckDiffCallback :
        DiffUtil.ItemCallback<StarDeckDbHelper.ManagerDeckRow>() {

        override fun areItemsTheSame(
            oldItem: StarDeckDbHelper.ManagerDeckRow,
            newItem: StarDeckDbHelper.ManagerDeckRow
        ): Boolean {
            return oldItem.deckId == newItem.deckId
        }

        override fun areContentsTheSame(
            oldItem: StarDeckDbHelper.ManagerDeckRow,
            newItem: StarDeckDbHelper.ManagerDeckRow
        ): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        private const val FILTER_ALL = "all"
        private const val KEY_STATUS_FILTER = "manager_decks_status_filter"
        private const val KEY_SEARCH_QUERY = "manager_decks_search_query"
    }
}