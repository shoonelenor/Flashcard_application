package com.example.stardeckapplication.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityManageReportReasonsBinding
import com.example.stardeckapplication.databinding.DialogReportReasonBinding
import com.example.stardeckapplication.databinding.ItemReportReasonBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.ReportReasonDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManageReportReasonsActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageReportReasonsBinding

    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val reasonDao by lazy { ReportReasonDao(dbHelper) }
    private val session by lazy { SessionManager(this) }

    private var all: List<ReportReasonDao.AdminReasonRow> = emptyList()
    private var statusFilter: Boolean? = null

    private val adapter = ReasonsAdapter(
        onEdit = { showEditDialog(it) },
        onToggle = { confirmToggle(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageReportReasonsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Report Reasons Setup"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        // Status chip click listeners
        fun selectStatusChip(selected: TextView) {
            listOf(b.chipStatusAll, b.chipStatusActive, b.chipStatusInactive).forEach { chip ->
                chip.setBackgroundResource(
                    if (chip == selected) R.drawable.bg_chip_selected
                    else R.drawable.bg_chip_default
                )
                chip.setTextColor(
                    if (chip == selected) getColor(android.R.color.white)
                    else getColor(R.color.stardeck_text_secondary)
                )
            }
        }

        b.chipStatusAll.setOnClickListener {
            selectStatusChip(b.chipStatusAll)
            statusFilter = null
            applyFilters()
        }
        b.chipStatusActive.setOnClickListener {
            selectStatusChip(b.chipStatusActive)
            statusFilter = true
            applyFilters()
        }
        b.chipStatusInactive.setOnClickListener {
            selectStatusChip(b.chipStatusInactive)
            statusFilter = false
            applyFilters()
        }

        b.fabAdd.setOnClickListener { showCreateDialog() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        all = reasonDao.adminGetAllReasons()
        applyFilters()
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = all

        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.label.lowercase().contains(q) ||
                        row.description.orEmpty().lowercase().contains(q)
            }
        }

        statusFilter?.let { activeOnly ->
            filtered = filtered.filter { it.isActive == activeOnly }
        }

        adapter.submitList(filtered)
        b.tvCount.text = "${filtered.size} reason(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val d = DialogReportReasonBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Report Reason"
        d.swActive.isChecked = true
        d.etSortOrder.setText(reasonDao.getNextSortOrder().toString())
        createReasonDialog(d, null).show()
    }

    private fun showEditDialog(row: ReportReasonDao.AdminReasonRow) {
        val d = DialogReportReasonBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Report Reason"
        d.etLabel.setText(row.label)
        d.etDescription.setText(row.description.orEmpty())
        d.etSortOrder.setText(row.sortOrder.toString())
        d.swActive.isChecked = row.isActive
        createReasonDialog(d, row).show()
    }

    private fun createReasonDialog(
        d: DialogReportReasonBinding,
        existing: ReportReasonDao.AdminReasonRow?
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton(if (existing == null) "Create" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilLabel.error = null
                d.tilDescription.error = null
                d.tilSortOrder.error = null

                val label = d.etLabel.text?.toString().orEmpty().trim()
                val description = d.etDescription.text?.toString().orEmpty().trim()
                val parsedSortOrder = d.etSortOrder.text?.toString().orEmpty().trim().toIntOrNull()
                val isActive = d.swActive.isChecked

                var ok = true

                if (label.length < 2) {
                    d.tilLabel.error = "At least 2 characters required"
                    ok = false
                } else if (label.length > 80) {
                    d.tilLabel.error = "Max 80 characters"
                    ok = false
                } else if (reasonDao.isLabelTaken(label, existing?.id)) {
                    d.tilLabel.error = "Report reason already exists"
                    ok = false
                }

                if (description.length > 200) {
                    d.tilDescription.error = "Max 200 characters"
                    ok = false
                }

                if (parsedSortOrder == null || parsedSortOrder < 0) {
                    d.tilSortOrder.error = "Enter 0 or higher"
                    ok = false
                }

                if (!ok) return@setOnClickListener

                val sortOrder = parsedSortOrder ?: 0

                try {
                    if (existing == null) {
                        reasonDao.createReason(
                            label = label,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder
                        )
                        Snackbar.make(b.root, "Report reason created", Snackbar.LENGTH_SHORT).show()
                    } else {
                        reasonDao.updateReason(
                            reasonId = existing.id,
                            label = label,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder
                        )
                        Snackbar.make(b.root, "Report reason updated", Snackbar.LENGTH_SHORT).show()
                    }
                    reload()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Could not save: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        return dialog
    }

    private fun confirmToggle(row: ReportReasonDao.AdminReasonRow) {
        val nextActive = !row.isActive
        MaterialAlertDialogBuilder(this)
            .setTitle("${if (nextActive) "Activate" else "Deactivate"} report reason?")
            .setMessage("This will ${if (nextActive) "activate" else "deactivate"} \"${row.label}\".")
            .setPositiveButton("Yes") { _, _ ->
                reasonDao.setReasonActive(row.id, nextActive)
                Snackbar.make(b.root, "Report reason ${if (nextActive) "activated" else "deactivated"}", Snackbar.LENGTH_SHORT).show()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(row: ReportReasonDao.AdminReasonRow) {
        if (row.usageCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cannot delete")
                .setMessage("This report reason has already been used in reports.\n\nDeactivate it instead so old reports stay safe.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete report reason?")
            .setMessage("This will permanently delete \"${row.label}\".")
            .setPositiveButton("Delete") { _, _ ->
                val rows = reasonDao.deleteReason(row.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Report reason deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete report reason", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class ReasonsAdapter(
        private val onEdit: (ReportReasonDao.AdminReasonRow) -> Unit,
        private val onToggle: (ReportReasonDao.AdminReasonRow) -> Unit,
        private val onDelete: (ReportReasonDao.AdminReasonRow) -> Unit
    ) : ListAdapter<ReportReasonDao.AdminReasonRow, ReasonsAdapter.VH>(Diff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportReasonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding, onEdit, onToggle, onDelete)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val binding: ItemReportReasonBinding,
            private val onEdit: (ReportReasonDao.AdminReasonRow) -> Unit,
            private val onToggle: (ReportReasonDao.AdminReasonRow) -> Unit,
            private val onDelete: (ReportReasonDao.AdminReasonRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: ReportReasonDao.AdminReasonRow) {
                binding.tvLabel.text = row.label
                binding.tvDescription.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                binding.chipStatus.text = if (row.isActive) "Active" else "Inactive"
                binding.chipSort.text = "Order: ${row.sortOrder}"
                binding.chipUsage.text = "Used: ${row.usageCount}"

                binding.btnEdit.setOnClickListener { onEdit(row) }
                binding.btnToggle.text = if (row.isActive) "Deactivate" else "Activate"
                binding.btnToggle.setOnClickListener { onToggle(row) }

                binding.btnDelete.isEnabled = row.usageCount == 0
                binding.btnDelete.setOnClickListener { onDelete(row) }
            }
        }

        private class Diff : DiffUtil.ItemCallback<ReportReasonDao.AdminReasonRow>() {
            override fun areItemsTheSame(oldItem: ReportReasonDao.AdminReasonRow, newItem: ReportReasonDao.AdminReasonRow): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ReportReasonDao.AdminReasonRow, newItem: ReportReasonDao.AdminReasonRow): Boolean = oldItem == newItem
        }
    }
}