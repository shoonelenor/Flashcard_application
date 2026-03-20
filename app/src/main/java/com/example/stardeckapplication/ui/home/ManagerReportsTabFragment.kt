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
import com.example.stardeckapplication.databinding.FragmentManagerReportsTabBinding
import com.example.stardeckapplication.databinding.ItemReportBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.manager.ManagerDeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerReportsTabFragment : Fragment(R.layout.fragment_manager_reports_tab) {

    private var _binding: FragmentManagerReportsTabBinding? = null
    private val binding get() = _binding!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    private var allReports: List<StarDeckDbHelper.ReportRow> = emptyList()

    private var reportStatusFilter: String = DbContract.REPORT_OPEN
    private var deckStatusFilter: String = FILTER_ALL
    private var searchQuery: String = ""

    private val reportsAdapter = ReportsAdapter(
        onView = { openDeck(it) },
        onHideToggle = { toggleDeck(it) },
        onResolve = { closeCase(it) },
        onDetails = { showDetails(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentManagerReportsTabBinding.bind(view)

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
        outState.putString(KEY_REPORT_STATUS_FILTER, reportStatusFilter)
        outState.putString(KEY_DECK_STATUS_FILTER, deckStatusFilter)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        reportStatusFilter =
            savedInstanceState?.getString(KEY_REPORT_STATUS_FILTER) ?: DbContract.REPORT_OPEN
        deckStatusFilter =
            savedInstanceState?.getString(KEY_DECK_STATUS_FILTER) ?: FILTER_ALL
        searchQuery =
            savedInstanceState?.getString(KEY_SEARCH_QUERY).orEmpty()
    }

    private fun setupRecycler() {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = reportsAdapter
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            applyFilters()
        }
    }

    private fun setupChips() {
        binding.chipOpen.setOnClickListener {
            setReportStatusFilter(DbContract.REPORT_OPEN)
        }

        binding.chipResolved.setOnClickListener {
            setReportStatusFilter(DbContract.REPORT_RESOLVED)
        }

        binding.chipAll.setOnClickListener {
            setReportStatusFilter(FILTER_ALL)
        }

        binding.chipDeckAll.setOnClickListener {
            setDeckStatusFilter(FILTER_ALL)
        }

        binding.chipDeckActive.setOnClickListener {
            setDeckStatusFilter(DbContract.DECK_ACTIVE)
        }

        binding.chipDeckHidden.setOnClickListener {
            setDeckStatusFilter(DbContract.DECK_HIDDEN)
        }
    }

    private fun applySavedUiState() {
        binding.etSearch.setText(searchQuery)
        updateReportStatusChips()
        updateDeckStatusChips()
    }

    private fun setReportStatusFilter(value: String) {
        reportStatusFilter = value
        updateReportStatusChips()
        applyFilters()
    }

    private fun setDeckStatusFilter(value: String) {
        deckStatusFilter = value
        updateDeckStatusChips()
        applyFilters()
    }

    private fun updateReportStatusChips() {
        binding.chipOpen.isChecked = reportStatusFilter == DbContract.REPORT_OPEN
        binding.chipResolved.isChecked = reportStatusFilter == DbContract.REPORT_RESOLVED
        binding.chipAll.isChecked = reportStatusFilter == FILTER_ALL
    }

    private fun updateDeckStatusChips() {
        binding.chipDeckAll.isChecked = deckStatusFilter == FILTER_ALL
        binding.chipDeckActive.isChecked = deckStatusFilter == DbContract.DECK_ACTIVE
        binding.chipDeckHidden.isChecked = deckStatusFilter == DbContract.DECK_HIDDEN
    }

    private fun safeReload() {
        try {
            allReports = db.managerGetReports()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbFixDialog(e)
        } catch (e: Exception) {
            showDbFixDialog(e)
        }
    }

    private fun applyFilters() {
        val q = searchQuery.trim().lowercase()

        var filtered = allReports

        filtered = when (reportStatusFilter) {
            DbContract.REPORT_OPEN -> filtered.filter { it.status == DbContract.REPORT_OPEN }
            DbContract.REPORT_RESOLVED -> filtered.filter { it.status == DbContract.REPORT_RESOLVED }
            else -> filtered
        }

        filtered = when (deckStatusFilter) {
            DbContract.DECK_ACTIVE -> filtered.filter { it.deckStatus == DbContract.DECK_ACTIVE }
            DbContract.DECK_HIDDEN -> filtered.filter { it.deckStatus == DbContract.DECK_HIDDEN }
            else -> filtered
        }

        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.deckTitle.lowercase().contains(q) ||
                        row.ownerEmail.lowercase().contains(q) ||
                        row.reporterEmail.lowercase().contains(q) ||
                        row.reason.lowercase().contains(q) ||
                        (row.details ?: "").lowercase().contains(q)
            }
        }

        reportsAdapter.submitList(filtered.toList())
        binding.tvCount.text = "${filtered.size} report(s)"

        val isEmpty = filtered.isEmpty()
        binding.groupEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (isEmpty) {
            if (allReports.isEmpty()) {
                binding.tvEmptyTitle.text = "No reports yet"
                binding.tvEmptyDesc.text = "When users report a deck, it will appear here."
            } else {
                binding.tvEmptyTitle.text = "No matches"
                binding.tvEmptyDesc.text = "Try changing filters or clearing the search."
            }
        }
    }

    private fun openDeck(row: StarDeckDbHelper.ReportRow) {
        val intent = Intent(requireContext(), ManagerDeckCardsActivity::class.java)
        intent.putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID, row.deckId)
        intent.putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE, row.deckTitle)
        intent.putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, row.ownerEmail)
        startActivity(intent)
    }

    private fun toggleDeck(row: StarDeckDbHelper.ReportRow) {
        val newStatus =
            if (row.deckStatus == DbContract.DECK_ACTIVE) DbContract.DECK_HIDDEN
            else DbContract.DECK_ACTIVE

        val actionTitle = if (newStatus == DbContract.DECK_HIDDEN) "Hide deck?" else "Unhide deck?"
        val actionButton = if (newStatus == DbContract.DECK_HIDDEN) "Hide deck" else "Unhide deck"
        val resultMessage = if (newStatus == DbContract.DECK_HIDDEN) {
            "Deck hidden"
        } else {
            "Deck visible again"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(actionTitle)
            .setMessage(
                "Deck: ${row.deckTitle}\n" +
                        "Owner: ${row.ownerEmail}\n\n" +
                        if (newStatus == DbContract.DECK_HIDDEN) {
                            "This deck will be hidden from users."
                        } else {
                            "This deck will become visible to users again."
                        }
            )
            .setPositiveButton(actionButton) { _, _ ->
                val rows = db.managerSetDeckStatus(row.deckId, newStatus)
                if (rows == 1) {
                    Snackbar.make(binding.root, resultMessage, Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not update deck", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeCase(row: StarDeckDbHelper.ReportRow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Close report?")
            .setMessage("This will mark the report as resolved.")
            .setPositiveButton("Close case") { _, _ ->
                val rows = db.managerResolveReport(row.reportId)
                if (rows == 1) {
                    Snackbar.make(binding.root, "Report closed", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not close report", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetails(row: StarDeckDbHelper.ReportRow) {
        val message = buildString {
            append("Deck: ${row.deckTitle}\n")
            append("Owner: ${row.ownerEmail}\n")
            append("Reported by: ${row.reporterEmail}\n")
            append("Case status: ${caseStatusText(row.status)}\n")
            append("Deck visibility: ${deckVisibilityText(row.deckStatus)}\n\n")
            append("Issue: ${row.reason}\n")
            if (!row.details.isNullOrBlank()) {
                append("\nDetails: ${row.details}")
            }
            append("\n\nReported at: ${formatTime(row.createdAt)}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun caseStatusText(status: String): String {
        return when (status) {
            DbContract.REPORT_OPEN -> "Open"
            DbContract.REPORT_RESOLVED -> "Resolved"
            else -> status.replaceFirstChar { it.uppercase() }
        }
    }

    private fun deckVisibilityText(status: String): String {
        return when (status) {
            DbContract.DECK_ACTIVE -> "Visible"
            DbContract.DECK_HIDDEN -> "Hidden"
            else -> status.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatTime(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    private fun showDbFixDialog(e: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Database refresh needed")
            .setMessage("Fix this once: uninstall app OR clear app data.\n\nError: ${e.message}")
            .setPositiveButton("OK") { _, _ -> requireActivity().finish() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ReportsAdapter(
        private val onView: (StarDeckDbHelper.ReportRow) -> Unit,
        private val onHideToggle: (StarDeckDbHelper.ReportRow) -> Unit,
        private val onResolve: (StarDeckDbHelper.ReportRow) -> Unit,
        private val onDetails: (StarDeckDbHelper.ReportRow) -> Unit
    ) : ListAdapter<StarDeckDbHelper.ReportRow, ReportsAdapter.VH>(ReportDiffCallback()) {

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = getItem(position).reportId

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onView, onHideToggle, onResolve, onDetails)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val binding: ItemReportBinding,
            private val onView: (StarDeckDbHelper.ReportRow) -> Unit,
            private val onHideToggle: (StarDeckDbHelper.ReportRow) -> Unit,
            private val onResolve: (StarDeckDbHelper.ReportRow) -> Unit,
            private val onDetails: (StarDeckDbHelper.ReportRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: StarDeckDbHelper.ReportRow) {
                val isVisible = row.deckStatus == DbContract.DECK_ACTIVE
                val isOpen = row.status == DbContract.REPORT_OPEN

                binding.tvDeck.text = row.deckTitle
                binding.tvMeta.text = "Owner: ${row.ownerEmail} • Reported by: ${row.reporterEmail}"
                binding.tvReason.text = "Issue: ${row.reason}"

                binding.chipCase.text = if (isOpen) "Case: Open" else "Case: Resolved"
                binding.chipDeck.text = if (isVisible) "Deck: Visible" else "Deck: Hidden"

                binding.btnView.text = "Review"
                binding.btnView.setOnClickListener { onView(row) }

                binding.btnHide.text = if (isVisible) "Hide" else "Unhide"
                binding.btnHide.setOnClickListener { onHideToggle(row) }

                binding.btnResolve.text = "Close"
                binding.btnResolve.visibility = if (isOpen) View.VISIBLE else View.GONE
                binding.btnResolve.setOnClickListener { onResolve(row) }

                binding.root.setOnClickListener { onDetails(row) }
            }
        }
    }

    private class ReportDiffCallback :
        DiffUtil.ItemCallback<StarDeckDbHelper.ReportRow>() {

        override fun areItemsTheSame(
            oldItem: StarDeckDbHelper.ReportRow,
            newItem: StarDeckDbHelper.ReportRow
        ): Boolean {
            return oldItem.reportId == newItem.reportId
        }

        override fun areContentsTheSame(
            oldItem: StarDeckDbHelper.ReportRow,
            newItem: StarDeckDbHelper.ReportRow
        ): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        private const val FILTER_ALL = "all"
        private const val KEY_REPORT_STATUS_FILTER = "manager_reports_report_status_filter"
        private const val KEY_DECK_STATUS_FILTER = "manager_reports_deck_status_filter"
        private const val KEY_SEARCH_QUERY = "manager_reports_search_query"
    }
}