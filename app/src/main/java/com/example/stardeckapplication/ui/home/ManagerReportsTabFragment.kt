package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.example.stardeckapplication.db.ModerationDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.manager.ManagerDeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerReportsTabFragment : Fragment(R.layout.fragment_manager_reports_tab) {

    private var _binding: FragmentManagerReportsTabBinding? = null
    private val binding get() = _binding!!

    private val session by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val managerDao by lazy { ModerationDao(dbHelper) }

    private var allReports: List<ModerationDao.ReportRow> = emptyList()
    private var lastFilteredReports: List<ModerationDao.ReportRow> = emptyList()

    private var reportStatusFilter: String = DbContract.REPORT_OPEN
    private var deckStatusFilter: String = FILTER_ALL
    private var selectedReason: String? = null
    private var searchQuery: String = ""

    private val reportsAdapter = ReportsAdapter(
        onView = { openDeck(it) },
        onHideToggle = { toggleDeck(it) },
        onCaseToggle = { toggleCase(it) },
        onDetails = { showDetails(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentManagerReportsTabBinding.bind(view)

        val me = session.load()
        val isAllowed = me != null && (
                me.role == DbContract.ROLE_MANAGER || me.role == DbContract.ROLE_ADMIN
                )

        if (!isAllowed) {
            requireActivity().finish()
            return
        }

        restoreUiState(savedInstanceState)
        setupRecycler()
        setupSearch()
        setupChips()
        setupReasonFilter()
        setupExportButton()
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
        outState.putString(KEY_REASON_FILTER, selectedReason)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        reportStatusFilter =
            savedInstanceState?.getString(KEY_REPORT_STATUS_FILTER) ?: DbContract.REPORT_OPEN
        deckStatusFilter =
            savedInstanceState?.getString(KEY_DECK_STATUS_FILTER) ?: FILTER_ALL
        selectedReason =
            savedInstanceState?.getString(KEY_REASON_FILTER)
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
        binding.chipOpen.setOnClickListener { setReportStatusFilter(DbContract.REPORT_OPEN) }
        binding.chipResolved.setOnClickListener { setReportStatusFilter(DbContract.REPORT_RESOLVED) }
        binding.chipAll.setOnClickListener { setReportStatusFilter(FILTER_ALL) }

        binding.chipDeckAll.setOnClickListener { setDeckStatusFilter(FILTER_ALL) }
        binding.chipDeckActive.setOnClickListener { setDeckStatusFilter(DbContract.DECK_ACTIVE) }
        binding.chipDeckHidden.setOnClickListener { setDeckStatusFilter(DbContract.DECK_HIDDEN) }
    }

    private fun setupReasonFilter() {
        binding.actReasonFilter.setOnClickListener {
            binding.actReasonFilter.showDropDown()
        }

        binding.actReasonFilter.setOnItemClickListener { parent, _, position, _ ->
            val chosen = parent.getItemAtPosition(position)?.toString().orEmpty()
            selectedReason = if (chosen == LABEL_ALL_REASONS) null else chosen
            applyFilters()
        }
    }

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener { shareReportsCsv() }
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
            allReports = managerDao.managerGetReports()
            updateSummaryCards()
            updateReasonFilterOptions()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbFixDialog(e)
        } catch (e: Exception) {
            showDbFixDialog(e)
        }
    }

    private fun updateSummaryCards() {
        val total = allReports.size
        val open = allReports.count { it.status == DbContract.REPORT_OPEN }
        val resolved = allReports.count { it.status == DbContract.REPORT_RESOLVED }
        val hidden = allReports.count { it.deckStatus == DbContract.DECK_HIDDEN }

        binding.tvSummaryTotal.text = total.toString()
        binding.tvSummaryOpen.text = open.toString()
        binding.tvSummaryResolved.text = resolved.toString()
        binding.tvSummaryHidden.text = hidden.toString()
    }

    private fun updateReasonFilterOptions() {
        val options = mutableListOf(LABEL_ALL_REASONS)
        options += allReports
            .map { it.reasonLabel.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        binding.actReasonFilter.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        )

        val displayText = selectedReason?.takeIf { options.contains(it) } ?: LABEL_ALL_REASONS
        if (displayText == LABEL_ALL_REASONS) {
            selectedReason = null
        }
        binding.actReasonFilter.setText(displayText, false)
    }

    private fun applyFilters() {
        val q = searchQuery.trim().lowercase()

        var filtered: List<ModerationDao.ReportRow> = allReports

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

        selectedReason?.let { reason ->
            filtered = filtered.filter { it.reasonLabel.equals(reason, ignoreCase = true) }
        }

        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.deckTitle.lowercase().contains(q) ||
                        row.ownerName.lowercase().contains(q) ||
                        row.ownerEmail.lowercase().contains(q) ||
                        row.reporterName.lowercase().contains(q) ||
                        row.reporterEmail.lowercase().contains(q) ||
                        row.reasonLabel.lowercase().contains(q) ||
                        row.reasonDescription.orEmpty().lowercase().contains(q) ||
                        row.details.orEmpty().lowercase().contains(q)
            }
        }

        lastFilteredReports = filtered.toList()
        reportsAdapter.submitList(filtered.toList())
        binding.tvCount.text = "${filtered.size} report(s) shown"

        val isEmpty = filtered.isEmpty()
        binding.groupEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (isEmpty) {
            if (allReports.isEmpty()) {
                binding.tvEmptyTitle.text = "No reports yet"
                binding.tvEmptyDesc.text = "When users report a deck, cases will appear here."
            } else {
                binding.tvEmptyTitle.text = "No matching reports"
                binding.tvEmptyDesc.text = "Try changing the filters or clearing search."
            }
        }
    }

    // ---------- EXPORT ----------

    private fun shareReportsCsv() {
        val list = lastFilteredReports
        if (list.isEmpty()) {
            Snackbar.make(
                binding.root,
                "No reports to export for current filters.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val csv = buildReportsCsv(list)
        saveReportsCsvToFile(csv)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StarDeck reports export")
            putExtra(Intent.EXTRA_TEXT, csv)
        }

        runCatching {
            startActivity(Intent.createChooser(sendIntent, "Share reports as CSV"))
        }.onFailure {
            Snackbar.make(
                binding.root,
                "No app found to share data (file is still saved).",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun saveReportsCsvToFile(csv: String) {
        val context = requireContext()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        if (dir == null) {
            Snackbar.make(binding.root, "Could not access Downloads folder.", Snackbar.LENGTH_LONG)
                .show()
            return
        }

        if (!dir.exists()) dir.mkdirs()

        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "stardeck_reports_$timeStamp.csv"
        val file = File(dir, fileName)

        try {
            FileOutputStream(file).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            val msg = "Saved to: Android/data/${context.packageName}/files/Download/$fileName"
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        } catch (e: IOException) {
            Snackbar.make(
                binding.root,
                "Could not save file: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun buildReportsCsv(list: List<ModerationDao.ReportRow>): String {
        val header = listOf(
            "Report ID",
            "Deck title",
            "Deck status",
            "Owner name",
            "Owner email",
            "Reporter name",
            "Reporter email",
            "Reason",
            "Reason guide",
            "Details",
            "Created at",
            "Case status"
        )

        val sb = StringBuilder()
        sb.appendLine(header.joinToString(",") { escapeCsv(it) })

        for (row in list) {
            val fields = listOf(
                row.reportId.toString(),
                row.deckTitle,
                deckVisibilityText(row.deckStatus),
                row.ownerName,
                row.ownerEmail,
                row.reporterName,
                row.reporterEmail,
                row.reasonLabel,
                row.reasonDescription.orEmpty(),
                row.details.orEmpty(),
                formatTime(row.createdAt),
                caseStatusText(row.status)
            )
            sb.appendLine(fields.joinToString(",") { escapeCsv(it) })
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    // ---------- ACTIONS ----------

    private fun openDeck(row: ModerationDao.ReportRow) {
        startActivity(
            Intent(requireContext(), ManagerDeckCardsActivity::class.java)
                .putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID, row.deckId)
                .putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE, row.deckTitle)
                .putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, row.ownerEmail)
        )
    }

    private fun toggleDeck(row: ModerationDao.ReportRow) {
        val newStatus =
            if (row.deckStatus == DbContract.DECK_ACTIVE) {
                DbContract.DECK_HIDDEN
            } else {
                DbContract.DECK_ACTIVE
            }

        val actionTitle =
            if (newStatus == DbContract.DECK_HIDDEN) "Hide deck?" else "Unhide deck?"
        val actionButton =
            if (newStatus == DbContract.DECK_HIDDEN) "Hide deck" else "Unhide deck"
        val resultMsg =
            if (newStatus == DbContract.DECK_HIDDEN) "Deck hidden" else "Deck visible again"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(actionTitle)
            .setMessage(
                "Deck: ${row.deckTitle}\n" +
                        "Owner: ${row.ownerName} (${row.ownerEmail})\n\n" +
                        if (newStatus == DbContract.DECK_HIDDEN) {
                            "This deck will be hidden from normal user access."
                        } else {
                            "This deck will become visible to users again."
                        }
            )
            .setPositiveButton(actionButton) { _, _ ->
                val rows = managerDao.managerSetDeckStatus(row.deckId, newStatus)
                if (rows == 1) {
                    Snackbar.make(binding.root, resultMsg, Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not update deck", Snackbar.LENGTH_LONG)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleCase(row: ModerationDao.ReportRow) {
        val newStatus =
            if (row.status == DbContract.REPORT_OPEN) {
                DbContract.REPORT_RESOLVED
            } else {
                DbContract.REPORT_OPEN
            }

        val dialogTitle =
            if (newStatus == DbContract.REPORT_RESOLVED) "Resolve report?" else "Reopen report?"
        val dialogButton =
            if (newStatus == DbContract.REPORT_RESOLVED) "Resolve" else "Reopen"
        val successMessage =
            if (newStatus == DbContract.REPORT_RESOLVED) "Report resolved" else "Report reopened"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setMessage(
                "Reason: ${row.reasonLabel}\n" +
                        "Deck: ${row.deckTitle}\n\n" +
                        if (newStatus == DbContract.REPORT_RESOLVED) {
                            "This case will be marked as resolved."
                        } else {
                            "This case will return to the open queue."
                        }
            )
            .setPositiveButton(dialogButton) { _, _ ->
                val rows = managerDao.managerSetReportStatus(row.reportId, newStatus)
                if (rows == 1) {
                    Snackbar.make(binding.root, successMessage, Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not update report", Snackbar.LENGTH_LONG)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetails(row: ModerationDao.ReportRow) {
        val message = buildString {
            append("Deck: ${row.deckTitle}\n")
            append("Owner: ${row.ownerName} (${row.ownerEmail})\n")
            append("Reported by: ${row.reporterName} (${row.reporterEmail})\n")
            append("Case status: ${caseStatusText(row.status)}\n")
            append("Deck visibility: ${deckVisibilityText(row.deckStatus)}\n")
            append("Reported at: ${formatTime(row.createdAt)}\n\n")
            append("Reason: ${row.reasonLabel}\n")
            if (!row.reasonDescription.isNullOrBlank()) {
                append("Reason guide: ${row.reasonDescription}\n")
            }
            if (!row.details.isNullOrBlank()) {
                append("\nDetails: ${row.details}")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- HELPERS ----------

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
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
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

    // ---------- ADAPTER ----------

    private class ReportsAdapter(
        private val onView: (ModerationDao.ReportRow) -> Unit,
        private val onHideToggle: (ModerationDao.ReportRow) -> Unit,
        private val onCaseToggle: (ModerationDao.ReportRow) -> Unit,
        private val onDetails: (ModerationDao.ReportRow) -> Unit
    ) : ListAdapter<ModerationDao.ReportRow, ReportsAdapter.VH>(ReportDiffCallback()) {

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = getItem(position).reportId

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onView, onHideToggle, onCaseToggle, onDetails)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val binding: ItemReportBinding,
            private val onView: (ModerationDao.ReportRow) -> Unit,
            private val onHideToggle: (ModerationDao.ReportRow) -> Unit,
            private val onCaseToggle: (ModerationDao.ReportRow) -> Unit,
            private val onDetails: (ModerationDao.ReportRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: ModerationDao.ReportRow) {
                val isVisible = row.deckStatus == DbContract.DECK_ACTIVE
                val isOpen = row.status == DbContract.REPORT_OPEN

                binding.tvDeck.text = row.deckTitle
                binding.tvMeta.text =
                    "Owner: ${row.ownerName} (${row.ownerEmail}) • " +
                            "Reported by: ${row.reporterName} (${row.reporterEmail})"

                binding.tvReason.text = "Reason: ${row.reasonLabel}"

                if (row.details.isNullOrBlank()) {
                    binding.tvDetailsPreview.visibility = View.GONE
                } else {
                    binding.tvDetailsPreview.visibility = View.VISIBLE
                    binding.tvDetailsPreview.text = "Details: ${row.details}"
                }

                binding.chipCase.text = if (isOpen) "Case: Open" else "Case: Resolved"
                binding.chipDeck.text = if (isVisible) "Deck: Visible" else "Deck: Hidden"
                binding.chipCreated.text =
                    "Reported: ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(row.createdAt))}"

                binding.btnCase.text = if (isOpen) "Resolve" else "Reopen"
                binding.btnCase.setOnClickListener { onCaseToggle(row) }

                binding.btnView.setOnClickListener { onView(row) }
                binding.btnHide.text = if (isVisible) "Hide" else "Unhide"
                binding.btnHide.setOnClickListener { onHideToggle(row) }
                binding.btnDetails.setOnClickListener { onDetails(row) }

                binding.root.setOnClickListener { onDetails(row) }
            }
        }
    }

    // ---------- DIFF ----------

    private class ReportDiffCallback : DiffUtil.ItemCallback<ModerationDao.ReportRow>() {
        override fun areItemsTheSame(
            oldItem: ModerationDao.ReportRow,
            newItem: ModerationDao.ReportRow
        ): Boolean = oldItem.reportId == newItem.reportId

        override fun areContentsTheSame(
            oldItem: ModerationDao.ReportRow,
            newItem: ModerationDao.ReportRow
        ): Boolean = oldItem == newItem
    }

    private companion object {
        private const val FILTER_ALL = "all"
        private const val LABEL_ALL_REASONS = "All reasons"

        private const val KEY_REPORT_STATUS_FILTER = "manager_reports_report_status_filter"
        private const val KEY_DECK_STATUS_FILTER = "manager_reports_deck_status_filter"
        private const val KEY_REASON_FILTER = "manager_reports_reason_filter"
        private const val KEY_SEARCH_QUERY = "manager_reports_search_query"
    }
}