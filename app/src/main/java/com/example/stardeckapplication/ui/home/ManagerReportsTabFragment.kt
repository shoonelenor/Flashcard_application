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
    private var deckStatusFilter: String = "all"

    private val reportsAdapter = ReportsAdapter(
        onView = { openDeck(it) },
        onHideToggle = { toggleDeck(it) },
        onResolve = { resolve(it) },
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

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = reportsAdapter

        binding.etSearch.doAfterTextChanged { applyFilters() }

        binding.chipOpen.setOnClickListener {
            reportStatusFilter = DbContract.REPORT_OPEN
            applyFilters()
        }

        binding.chipResolved.setOnClickListener {
            reportStatusFilter = DbContract.REPORT_RESOLVED
            applyFilters()
        }

        binding.chipAll.setOnClickListener {
            reportStatusFilter = "all"
            applyFilters()
        }

        binding.chipDeckAll.setOnClickListener {
            deckStatusFilter = "all"
            applyFilters()
        }

        binding.chipDeckActive.setOnClickListener {
            deckStatusFilter = DbContract.DECK_ACTIVE
            applyFilters()
        }

        binding.chipDeckHidden.setOnClickListener {
            deckStatusFilter = DbContract.DECK_HIDDEN
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
            allReports = db.managerGetReports()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbFixDialog(e)
        } catch (e: Exception) {
            showDbFixDialog(e)
        }
    }

    private fun applyFilters() {
        val q = binding.etSearch.text?.toString().orEmpty().trim().lowercase()

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

        reportsAdapter.submit(filtered)

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

        val actionWord = if (newStatus == DbContract.DECK_HIDDEN) "Hide" else "Unhide"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$actionWord deck?")
            .setMessage("Deck: ${row.deckTitle}\nOwner: ${row.ownerEmail}")
            .setPositiveButton(actionWord) { _, _ ->
                val rows = db.managerSetDeckStatus(row.deckId, newStatus)
                if (rows == 1) {
                    Snackbar.make(binding.root, "Deck updated", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not update deck", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resolve(row: StarDeckDbHelper.ReportRow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Resolve report?")
            .setMessage("Mark this report as resolved?")
            .setPositiveButton("Resolve") { _, _ ->
                val rows = db.managerResolveReport(row.reportId)
                if (rows == 1) {
                    Snackbar.make(binding.root, "Resolved", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(binding.root, "Could not resolve", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetails(row: StarDeckDbHelper.ReportRow) {
        val message = buildString {
            append("Deck: ${row.deckTitle}\n")
            append("Owner: ${row.ownerEmail}\n")
            append("Reporter: ${row.reporterEmail}\n")
            append("Report status: ${row.status}\n")
            append("Deck status: ${row.deckStatus}\n\n")
            append("Reason: ${row.reason}\n")
            if (!row.details.isNullOrBlank()) {
                append("\nDetails: ${row.details}")
            }
            append("\n\nTime: ${formatTime(row.createdAt)}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
    ) : RecyclerView.Adapter<ReportsAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.ReportRow>()

        fun submit(newItems: List<StarDeckDbHelper.ReportRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onView, onHideToggle, onResolve, onDetails)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val binding: ItemReportBinding,
            private val onView: (StarDeckDbHelper.ReportRow) -> Unit,
            private val onHideToggle: (StarDeckDbHelper.ReportRow) -> Unit,
            private val onResolve: (StarDeckDbHelper.ReportRow) -> Unit,
            private val onDetails: (StarDeckDbHelper.ReportRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: StarDeckDbHelper.ReportRow) {
                binding.tvDeck.text = row.deckTitle
                binding.tvMeta.text = "Owner: ${row.ownerEmail} • Reporter: ${row.reporterEmail}"
                binding.tvReason.text = "Reason: ${row.reason}"
                binding.tvStatus.text = "Report: ${row.status} • Deck: ${row.deckStatus}"

                binding.btnView.setOnClickListener { onView(row) }

                binding.btnHide.text =
                    if (row.deckStatus == DbContract.DECK_ACTIVE) "Hide" else "Unhide"

                binding.btnHide.setOnClickListener { onHideToggle(row) }

                val canResolve = row.status == DbContract.REPORT_OPEN
                binding.btnResolve.isEnabled = canResolve
                binding.btnResolve.alpha = if (canResolve) 1f else 0.5f
                binding.btnResolve.setOnClickListener { onResolve(row) }

                binding.root.setOnClickListener { onDetails(row) }
            }
        }
    }
}