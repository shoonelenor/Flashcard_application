package com.example.stardeckapplication.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManageAchievementsBinding
import com.example.stardeckapplication.databinding.DialogAchievementBinding
import com.example.stardeckapplication.databinding.ItemAdminAchievementBinding
import com.example.stardeckapplication.db.AchievementDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManageAchievementsActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageAchievementsBinding

    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val achievementDao by lazy { AchievementDao(dbHelper) }
    private val session by lazy { SessionManager(this) }

    private var all: List<AchievementDao.AdminAchievementRow> = emptyList()
    private var statusFilter: Boolean? = null

    private val adapter = AchievementsAdapter(
        onEdit = { showEditDialog(it) },
        onToggle = { confirmToggle(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageAchievementsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        achievementDao.ensureDefaultsInserted()

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Achievement Setup"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }
        b.chipStatusGroup.setOnCheckedStateChangeListener { _, _ ->
            statusFilter = when {
                b.chipStatusActive.isChecked -> true
                b.chipStatusInactive.isChecked -> false
                else -> null
            }
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
        all = achievementDao.adminGetAllAchievements()
        applyFilters()
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = all
        if (q.isNotBlank()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                        it.description.orEmpty().lowercase().contains(q) ||
                        it.metricLabel.lowercase().contains(q)
            }
        }
        statusFilter?.let { activeOnly ->
            filtered = filtered.filter { it.isActive == activeOnly }
        }

        adapter.submitList(filtered)
        b.tvCount.text = "${filtered.size} achievement(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showCreateDialog() {
        val d = DialogAchievementBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Achievement"
        d.swActive.isChecked = true
        d.etSortOrder.setText(achievementDao.getNextSortOrder().toString())
        val metricOptions = bindMetricDropdown(d, null)
        createAchievementDialog(d, null, metricOptions).show()
    }

    private fun showEditDialog(row: AchievementDao.AdminAchievementRow) {
        val d = DialogAchievementBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Achievement"
        d.etTitle.setText(row.title)
        d.etDescription.setText(row.description.orEmpty())
        d.etTargetValue.setText(row.targetValue.toString())
        d.etSortOrder.setText(row.sortOrder.toString())
        d.swActive.isChecked = row.isActive
        val metricOptions = bindMetricDropdown(d, row.metricKey)
        createAchievementDialog(d, row, metricOptions).show()
    }

    private fun bindMetricDropdown(
        d: DialogAchievementBinding,
        selectedMetricKey: String?
    ): List<AchievementDao.MetricOption> {
        val options = achievementDao.getMetricOptions()
        val labels = options.map { it.label }

        d.actMetric.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        d.actMetric.setOnClickListener { d.actMetric.showDropDown() }

        val selected = options.firstOrNull { it.key == selectedMetricKey } ?: options.firstOrNull()
        if (selected != null) {
            d.actMetric.setText(selected.label, false)
            d.tvMetricHint.text = selected.hint
        }

        d.actMetric.setOnItemClickListener { _, _, position, _ ->
            d.tvMetricHint.text = options[position].hint
        }

        return options
    }

    private fun createAchievementDialog(
        d: DialogAchievementBinding,
        existing: AchievementDao.AdminAchievementRow?,
        metricOptions: List<AchievementDao.MetricOption>
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton(if (existing == null) "Create" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilTitle.error = null
                d.tilDescription.error = null
                d.tilMetric.error = null
                d.tilTargetValue.error = null
                d.tilSortOrder.error = null

                val title = d.etTitle.text?.toString().orEmpty().trim()
                val description = d.etDescription.text?.toString().orEmpty().trim()
                val metricKey = metricOptions.firstOrNull {
                    it.label.equals(d.actMetric.text?.toString().orEmpty().trim(), ignoreCase = true)
                }?.key
                val targetValue = d.etTargetValue.text?.toString().orEmpty().trim().toIntOrNull()
                val sortOrder = d.etSortOrder.text?.toString().orEmpty().trim().toIntOrNull()
                val isActive = d.swActive.isChecked

                var ok = true
                if (title.length < 2) {
                    d.tilTitle.error = "At least 2 characters required"
                    ok = false
                } else if (title.length > 60) {
                    d.tilTitle.error = "Max 60 characters"
                    ok = false
                } else if (achievementDao.isTitleTaken(title, existing?.id)) {
                    d.tilTitle.error = "Achievement already exists"
                    ok = false
                }

                if (description.length > 200) {
                    d.tilDescription.error = "Max 200 characters"
                    ok = false
                }
                if (metricKey == null) {
                    d.tilMetric.error = "Choose a valid metric"
                    ok = false
                }
                if (targetValue == null || targetValue <= 0) {
                    d.tilTargetValue.error = "Enter 1 or higher"
                    ok = false
                }
                if (sortOrder == null || sortOrder < 0) {
                    d.tilSortOrder.error = "Enter 0 or higher"
                    ok = false
                }
                if (!ok || metricKey == null || targetValue == null || sortOrder == null) return@setOnClickListener

                try {
                    if (existing == null) {
                        achievementDao.createAchievement(title, description.ifBlank { null }, metricKey, targetValue, isActive, sortOrder)
                        Snackbar.make(b.root, "Achievement created", Snackbar.LENGTH_SHORT).show()
                    } else {
                        achievementDao.updateAchievement(existing.id, title, description.ifBlank { null }, metricKey, targetValue, isActive, sortOrder)
                        Snackbar.make(b.root, "Achievement updated", Snackbar.LENGTH_SHORT).show()
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

    private fun confirmToggle(row: AchievementDao.AdminAchievementRow) {
        val nextActive = !row.isActive
        MaterialAlertDialogBuilder(this)
            .setTitle("${if (nextActive) "Activate" else "Deactivate"} achievement?")
            .setMessage("This will ${if (nextActive) "activate" else "deactivate"} \"${row.title}\".")
            .setPositiveButton("Yes") { _, _ ->
                achievementDao.setAchievementActive(row.id, nextActive)
                Snackbar.make(b.root, "Achievement ${if (nextActive) "activated" else "deactivated"}", Snackbar.LENGTH_SHORT).show()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(row: AchievementDao.AdminAchievementRow) {
        if (row.unlockCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cannot delete")
                .setMessage("This achievement has already been unlocked by users.\n\nDeactivate it instead so old achievement history stays safe.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete achievement?")
            .setMessage("This will permanently delete \"${row.title}\".")
            .setPositiveButton("Delete") { _, _ ->
                val rows = achievementDao.deleteAchievement(row.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Achievement deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete achievement", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class AchievementsAdapter(
        private val onEdit: (AchievementDao.AdminAchievementRow) -> Unit,
        private val onToggle: (AchievementDao.AdminAchievementRow) -> Unit,
        private val onDelete: (AchievementDao.AdminAchievementRow) -> Unit
    ) : ListAdapter<AchievementDao.AdminAchievementRow, AchievementsAdapter.VH>(Diff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAdminAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding, onEdit, onToggle, onDelete)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val binding: ItemAdminAchievementBinding,
            private val onEdit: (AchievementDao.AdminAchievementRow) -> Unit,
            private val onToggle: (AchievementDao.AdminAchievementRow) -> Unit,
            private val onDelete: (AchievementDao.AdminAchievementRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: AchievementDao.AdminAchievementRow) {
                binding.tvTitle.text = row.title
                binding.tvMetric.text = "${row.metricLabel} • Target ${row.targetValue}"
                binding.tvDescription.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                binding.chipStatus.text = if (row.isActive) "Active" else "Inactive"
                binding.chipSort.text = "Order: ${row.sortOrder}"
                binding.chipUnlocks.text = "Unlocked: ${row.unlockCount}"

                binding.btnEdit.setOnClickListener { onEdit(row) }
                binding.btnToggle.text = if (row.isActive) "Deactivate" else "Activate"
                binding.btnToggle.setOnClickListener { onToggle(row) }

                binding.btnDelete.isEnabled = row.unlockCount == 0
                binding.btnDelete.setOnClickListener { onDelete(row) }
            }
        }

        private class Diff : DiffUtil.ItemCallback<AchievementDao.AdminAchievementRow>() {
            override fun areItemsTheSame(oldItem: AchievementDao.AdminAchievementRow, newItem: AchievementDao.AdminAchievementRow): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: AchievementDao.AdminAchievementRow, newItem: AchievementDao.AdminAchievementRow): Boolean = oldItem == newItem
        }
    }
}