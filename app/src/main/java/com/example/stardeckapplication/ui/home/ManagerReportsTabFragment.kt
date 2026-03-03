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

    private var _b: FragmentManagerReportsTabBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val db by lazy { StarDeckDbHelper(requireContext()) }

    private var all: List<StarDeckDbHelper.ReportRow> = emptyList()
    private var reportStatusFilter: String = DbContract.REPORT_OPEN
    private var deckStatusFilter: String = "all"

    private val adapter = ReportsAdapter(
        onView = { openDeck(it) },
        onHideToggle = { toggleDeck(it) },
        onResolve = { resolve(it) },
        onDetails = { showDetails(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentManagerReportsTabBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            requireActivity().finish(); return
        }

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        b.chipOpen.setOnClickListener { reportStatusFilter = DbContract.REPORT_OPEN; applyFilters() }
        b.chipResolved.setOnClickListener { reportStatusFilter = DbContract.REPORT_RESOLVED; applyFilters() }
        b.chipAll.setOnClickListener { reportStatusFilter = "all"; applyFilters() }

        b.chipDeckAll.setOnClickListener { deckStatusFilter = "all"; applyFilters() }
        b.chipDeckActive.setOnClickListener { deckStatusFilter = DbContract.DECK_ACTIVE; applyFilters() }
        b.chipDeckHidden.setOnClickListener { deckStatusFilter = DbContract.DECK_HIDDEN; applyFilters() }

        safeReload()
    }

    override fun onResume() {
        super.onResume()
        safeReload()
    }

    private fun safeReload() {
        try {
            all = db.managerGetReports()
            applyFilters()
        } catch (e: SQLiteException) {
            showDbFixDialog(e)
        } catch (e: Exception) {
            showDbFixDialog(e)
        }
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()
        var filtered = all

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
            filtered = filtered.filter {
                it.deckTitle.lowercase().contains(q) ||
                        it.ownerEmail.lowercase().contains(q) ||
                        it.reporterEmail.lowercase().contains(q) ||
                        it.reason.lowercase().contains(q) ||
                        (it.details ?: "").lowercase().contains(q)
            }
        }

        adapter.submit(filtered)
        b.tvCount.text = "${filtered.size} report(s)"

        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE

        if (empty) {
            if (all.isEmpty()) {
                b.tvEmptyTitle.text = "No reports yet"
                b.tvEmptyDesc.text = "When users report a deck, it will appear here."
            } else {
                b.tvEmptyTitle.text = "No matches"
                b.tvEmptyDesc.text = "Try changing filters or clearing the search."
            }
        }
    }

    private fun openDeck(r: StarDeckDbHelper.ReportRow) {
        startActivity(
            Intent(requireContext(), ManagerDeckCardsActivity::class.java).apply {
                putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID, r.deckId)
                putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE, r.deckTitle)
                putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, r.ownerEmail)
            }
        )
    }

    private fun toggleDeck(r: StarDeckDbHelper.ReportRow) {
        val newStatus =
            if (r.deckStatus == DbContract.DECK_ACTIVE) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE
        val word = if (newStatus == DbContract.DECK_HIDDEN) "Hide" else "Unhide"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$word deck?")
            .setMessage("Deck: ${r.deckTitle}\nOwner: ${r.ownerEmail}")
            .setPositiveButton(word) { _, _ ->
                val rows = db.managerSetDeckStatus(r.deckId, newStatus)
                if (rows == 1) {
                    Snackbar.make(b.root, "Deck updated", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(b.root, "Could not update deck", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resolve(r: StarDeckDbHelper.ReportRow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Resolve report?")
            .setMessage("Mark this report as resolved?")
            .setPositiveButton("Resolve") { _, _ ->
                val rows = db.managerResolveReport(r.reportId)
                if (rows == 1) {
                    Snackbar.make(b.root, "Resolved", Snackbar.LENGTH_SHORT).show()
                    safeReload()
                } else {
                    Snackbar.make(b.root, "Could not resolve", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetails(r: StarDeckDbHelper.ReportRow) {
        val msg = buildString {
            append("Deck: ${r.deckTitle}\n")
            append("Owner: ${r.ownerEmail}\n")
            append("Reporter: ${r.reporterEmail}\n")
            append("Report status: ${r.status}\n")
            append("Deck status: ${r.deckStatus}\n\n")
            append("Reason: ${r.reason}\n")
            if (!r.details.isNullOrBlank()) append("\nDetails: ${r.details}")
            append("\n\nTime: ${fmt(r.createdAt)}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report details")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun fmt(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    private fun showDbFixDialog(e: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Database refresh needed")
            .setMessage("Fix (do once): uninstall the app OR clear app data.\n\nError: ${e.message}")
            .setPositiveButton("OK") { _, _ -> requireActivity().finish() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private class ReportsAdapter(
        val onView: (StarDeckDbHelper.ReportRow) -> Unit,
        val onHideToggle: (StarDeckDbHelper.ReportRow) -> Unit,
        val onResolve: (StarDeckDbHelper.ReportRow) -> Unit,
        val onDetails: (StarDeckDbHelper.ReportRow) -> Unit
    ) : RecyclerView.Adapter<ReportsAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.ReportRow>()

        fun submit(newItems: List<StarDeckDbHelper.ReportRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onView, onHideToggle, onResolve, onDetails)
        }

        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        class VH(
            private val b: ItemReportBinding,
            val onView: (StarDeckDbHelper.ReportRow) -> Unit,
            val onHideToggle: (StarDeckDbHelper.ReportRow) -> Unit,
            val onResolve: (StarDeckDbHelper.ReportRow) -> Unit,
            val onDetails: (StarDeckDbHelper.ReportRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(r: StarDeckDbHelper.ReportRow) {
                b.tvDeck.text = r.deckTitle
                b.tvMeta.text = "Owner: ${r.ownerEmail} • Reporter: ${r.reporterEmail}"
                b.tvReason.text = "Reason: ${r.reason}"
                b.tvStatus.text = "Report: ${r.status} • Deck: ${r.deckStatus}"

                b.btnView.setOnClickListener { onView(r) }
                b.btnHide.text = if (r.deckStatus == DbContract.DECK_ACTIVE) "Hide" else "Unhide"
                b.btnHide.setOnClickListener { onHideToggle(r) }

                val canResolve = r.status == DbContract.REPORT_OPEN
                b.btnResolve.isEnabled = canResolve
                b.btnResolve.alpha = if (canResolve) 1f else 0.5f
                b.btnResolve.setOnClickListener { onResolve(r) }

                b.root.setOnClickListener { onDetails(r) }
            }
        }
    }
}