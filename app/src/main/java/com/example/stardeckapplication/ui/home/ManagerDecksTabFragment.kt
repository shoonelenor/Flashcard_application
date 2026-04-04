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
import com.example.stardeckapplication.db.ModerationDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.manager.ManagerDeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManagerDecksTabFragment : Fragment(R.layout.fragment_manager_decks_tab) {

    private var _binding: FragmentManagerDecksTabBinding? = null
    private val binding get() = _binding!!

    private val session    by lazy { SessionManager(requireContext()) }
    private val dbHelper   by lazy { StarDeckDbHelper(requireContext()) }
    private val managerDao by lazy { ModerationDao(dbHelper) }

    private var allDecks     : List<ModerationDao.ManagerDeckRow> = emptyList()
    private var statusFilter : String = FILTER_ALL
    private var searchQuery  : String = ""

    private val decksAdapter = DecksAdapter(
        onView   = { openDeck(it) },
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
        outState.putString(KEY_SEARCH_QUERY,  searchQuery)
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        statusFilter = savedInstanceState?.getString(KEY_STATUS_FILTER) ?: FILTER_ALL
        searchQuery  = savedInstanceState?.getString(KEY_SEARCH_QUERY).orEmpty()
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
        binding.chipAll.setOnClickListener    { setStatusFilter(FILTER_ALL) }
        binding.chipActive.setOnClickListener { setStatusFilter(DbContract.DECK_ACTIVE) }
        binding.chipHidden.setOnClickListener { setStatusFilter(DbContract.DECK_HIDDEN) }
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
        binding.chipAll.isChecked    = statusFilter == FILTER_ALL
        binding.chipActive.isChecked = statusFilter == DbContract.DECK_ACTIVE
        binding.chipHidden.isChecked = statusFilter == DbContract.DECK_HIDDEN
    }

    private fun safeReload() {
        try {
            allDecks = managerDao.managerGetAllDecks()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbError(e)
        } catch (e: Exception) {
            showDbError(e)
        }
    }

    private fun applyFilters() {
        val q = searchQuery.trim().lowercase()

        var filtered: List<ModerationDao.ManagerDeckRow> = allDecks

        filtered = when (statusFilter) {
            DbContract.DECK_ACTIVE -> filtered.filter { it.status == DbContract.DECK_ACTIVE }
            DbContract.DECK_HIDDEN -> filtered.filter { it.status == DbContract.DECK_HIDDEN }
            else                   -> filtered
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
        binding.recycler.visibility   = if (isEmpty) View.GONE    else View.VISIBLE

        if (isEmpty) {
            if (allDecks.isEmpty()) {
                binding.tvEmptyTitle.text = "No decks yet"
                binding.tvEmptyDesc.text  = "Decks will appear here after users create them."
            } else {
                binding.tvEmptyTitle.text = "No matches"
                binding.tvEmptyDesc.text  = "Try changing filters or clearing search."
            }
        }
    }

    private fun openDeck(row: ModerationDao.ManagerDeckRow) {
        startActivity(
            Intent(requireContext(), ManagerDeckCardsActivity::class.java)
                .putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID,    row.deckId)
                .putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE,  row.title)
                .putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, row.ownerEmail)
        )
    }

    private fun toggleDeckStatus(row: ModerationDao.ManagerDeckRow) {
        val newStatus  = if (row.status == DbContract.DECK_ACTIVE) DbContract.DECK_HIDDEN
        else DbContract.DECK_ACTIVE
        val actionWord = if (newStatus == DbContract.DECK_HIDDEN) "Hide" else "Unhide"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$actionWord deck?")
            .setMessage(
                "Deck: ${row.title}\nOwner: ${row.ownerEmail}\n\n" +
                        if (newStatus == DbContract.DECK_HIDDEN)
                            "This will remove it from user view."
                        else
                            "This will make it visible to users."
            )
            .setPositiveButton(actionWord) { _, _ ->
                val rows = managerDao.managerSetDeckStatus(row.deckId, newStatus)
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

    // ---------- ADAPTER ----------

    private class DecksAdapter(
        private val onView   : (ModerationDao.ManagerDeckRow) -> Unit,
        private val onToggle : (ModerationDao.ManagerDeckRow) -> Unit
    ) : ListAdapter<ModerationDao.ManagerDeckRow, DecksAdapter.VH>(DeckDiffCallback()) {

        init { setHasStableIds(true) }

        override fun getItemId(position: Int): Long = getItem(position).deckId

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemManagerDeckTabBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b, onView, onToggle)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class VH(
            private val b        : ItemManagerDeckTabBinding,
            private val onView   : (ModerationDao.ManagerDeckRow) -> Unit,
            private val onToggle : (ModerationDao.ManagerDeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: ModerationDao.ManagerDeckRow) {
                b.tvTitle.text  = row.title
                b.tvOwner.text  = "Owner: ${row.ownerEmail}"
                b.tvMeta.text   = "${row.cardCount} card(s) • ${row.status}"
                b.tvDesc.text   = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.btnView.setOnClickListener   { onView(row) }
                b.btnToggle.text               = if (row.status == DbContract.DECK_ACTIVE) "Hide" else "Unhide"
                b.btnToggle.setOnClickListener { onToggle(row) }
                // ✅ Fixed: was b.chipStatus (Chip), now b.btnStatus (MaterialButton)
                b.btnStatus.text = if (row.status == DbContract.DECK_ACTIVE) "Active" else "Hidden"
            }
        }
    }

    // ---------- DIFF ----------

    private class DeckDiffCallback : DiffUtil.ItemCallback<ModerationDao.ManagerDeckRow>() {
        override fun areItemsTheSame(
            oldItem: ModerationDao.ManagerDeckRow,
            newItem: ModerationDao.ManagerDeckRow
        ): Boolean = oldItem.deckId == newItem.deckId

        override fun areContentsTheSame(
            oldItem: ModerationDao.ManagerDeckRow,
            newItem: ModerationDao.ManagerDeckRow
        ): Boolean = oldItem == newItem
    }

    private companion object {
        private const val FILTER_ALL         = "all"
        private const val KEY_STATUS_FILTER  = "manager_decks_status_filter"
        private const val KEY_SEARCH_QUERY   = "manager_decks_search_query"
    }
}