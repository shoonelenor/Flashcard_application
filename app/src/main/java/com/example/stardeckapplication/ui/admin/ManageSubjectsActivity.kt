package com.example.stardeckapplication.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManageSubjectsBinding
import com.example.stardeckapplication.databinding.DialogSubjectBinding
import com.example.stardeckapplication.databinding.ItemSubjectBinding
import com.example.stardeckapplication.db.CategoryDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.SubjectDao
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManageSubjectsActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageSubjectsBinding

    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val subjectDao by lazy { SubjectDao(dbHelper) }
    private val categoryDao by lazy { CategoryDao(dbHelper) }
    private val session by lazy { SessionManager(this) }

    private var all: List<SubjectDao.AdminSubjectRow> = emptyList()
    private var statusFilter: Boolean? = null

    private val adapter = SubjectsAdapter(
        onEdit = { showEditDialog(it) },
        onToggle = { confirmToggle(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageSubjectsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Subject Setup"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        b.chipStatusActive.setOnCheckedChangeListener { _, _ ->
            statusFilter = when {
                b.chipStatusActive.isChecked -> true
                b.chipStatusInactive.isChecked -> false
                else -> null
            }
            applyFilters()
        }
        b.chipStatusInactive.setOnCheckedChangeListener { _, _ ->
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
        all = subjectDao.adminGetAllSubjects()
        applyFilters()
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = all

        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.name.lowercase().contains(q) ||
                        row.categoryName.lowercase().contains(q) ||
                        row.description.orEmpty().lowercase().contains(q)
            }
        }

        statusFilter?.let { activeOnly ->
            filtered = filtered.filter { it.isActive == activeOnly }
        }

        adapter.submitList(filtered)
        b.tvCount.text = "${filtered.size} subject(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val d = DialogSubjectBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Subject"
        d.swActive.isChecked = true
        val categories = bindCategoryDropdown(d, null)
        bindSortOrderPreview(d, categories, null)
        createSubjectDialog(d, null, categories).show()
    }

    private fun showEditDialog(row: SubjectDao.AdminSubjectRow) {
        val d = DialogSubjectBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Subject"
        d.etName.setText(row.name)
        d.etDescription.setText(row.description.orEmpty())
        d.swActive.isChecked = row.isActive
        val categories = bindCategoryDropdown(d, row.categoryId)
        d.etSortOrder.setText(row.sortOrder.toString())
        createSubjectDialog(d, row, categories).show()
    }

    private fun bindCategoryDropdown(
        d: DialogSubjectBinding,
        selectedCategoryId: Long?
    ): List<CategoryDao.SelectableCategory> {
        val categories = categoryDao.getSelectableCategories(selectedCategoryId)
        val labels = categories.map { it.name }

        d.actCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        d.actCategory.setOnClickListener { d.actCategory.showDropDown() }

        val selectedLabel = categories.firstOrNull { it.id == selectedCategoryId }?.name
        if (selectedLabel != null) {
            d.actCategory.setText(selectedLabel, false)
        } else if (labels.isNotEmpty()) {
            d.actCategory.setText(labels.first(), false)
        }

        return categories
    }

    private fun bindSortOrderPreview(
        d: DialogSubjectBinding,
        categories: List<CategoryDao.SelectableCategory>,
        existing: SubjectDao.AdminSubjectRow?
    ) {
        d.actCategory.setOnItemClickListener { _, _, _, _ ->
            if (existing == null) {
                val categoryId = resolveCategoryId(
                    d.actCategory.text?.toString().orEmpty(),
                    categories
                )
                if (categoryId != null && categoryId > 0L) {
                    d.etSortOrder.setText(subjectDao.getNextSortOrder(categoryId).toString())
                }
            }
        }

        if (existing == null && categories.isNotEmpty()) {
            val firstCategoryId = resolveCategoryId(
                d.actCategory.text?.toString().orEmpty(),
                categories
            )
            if (firstCategoryId != null && firstCategoryId > 0L) {
                d.etSortOrder.setText(subjectDao.getNextSortOrder(firstCategoryId).toString())
            }
        }
    }

    private fun createSubjectDialog(
        d: DialogSubjectBinding,
        existing: SubjectDao.AdminSubjectRow?,
        categories: List<CategoryDao.SelectableCategory>
    ): AlertDialog {
        val positiveText = if (existing == null) "Create" else "Save"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton(positiveText, null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilCategory.error = null
                d.tilName.error = null
                d.tilDescription.error = null
                d.tilSortOrder.error = null

                val categoryId = resolveCategoryId(
                    d.actCategory.text?.toString().orEmpty(),
                    categories
                )
                val name = d.etName.text?.toString().orEmpty().trim()
                val description = d.etDescription.text?.toString().orEmpty().trim()
                val parsedSortOrder = d.etSortOrder.text?.toString().orEmpty().trim().toIntOrNull()
                val isActive = d.swActive.isChecked

                var ok = true

                if (categoryId == null) {
                    d.tilCategory.error = "Please choose a valid category"
                    ok = false
                }

                if (name.length < 2) {
                    d.tilName.error = "At least 2 characters required"
                    ok = false
                } else if (name.length > 60) {
                    d.tilName.error = "Max 60 characters"
                    ok = false
                } else if (categoryId != null && subjectDao.isNameTaken(categoryId, name, existing?.id)) {
                    d.tilName.error = "Subject already exists in this category"
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

                if (!ok || categoryId == null) return@setOnClickListener

                val sortOrder = parsedSortOrder ?: 0

                try {
                    if (existing == null) {
                        subjectDao.createSubject(
                            categoryId = categoryId,
                            name = name,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder
                        )
                        Snackbar.make(b.root, "Subject created", Snackbar.LENGTH_SHORT).show()
                    } else {
                        subjectDao.updateSubject(
                            subjectId = existing.id,
                            categoryId = categoryId,
                            name = name,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder
                        )
                        Snackbar.make(b.root, "Subject updated", Snackbar.LENGTH_SHORT).show()
                    }

                    reload()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Snackbar.make(
                        b.root,
                        "Could not save: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }

        return dialog
    }

    private fun resolveCategoryId(
        rawText: String,
        categories: List<CategoryDao.SelectableCategory>
    ): Long? {
        val clean = rawText.trim()
        val matched = categories.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        return matched?.id
    }

    private fun confirmToggle(row: SubjectDao.AdminSubjectRow) {
        val nextActive = !row.isActive

        MaterialAlertDialogBuilder(this)
            .setTitle("${if (nextActive) "Activate" else "Deactivate"} subject?")
            .setMessage("This will ${if (nextActive) "activate" else "deactivate"} \"${row.name}\".")
            .setPositiveButton("Yes") { _, _ ->
                subjectDao.setSubjectActive(row.id, nextActive)
                Snackbar.make(
                    b.root,
                    "Subject ${if (nextActive) "activated" else "deactivated"}",
                    Snackbar.LENGTH_SHORT
                ).show()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(row: SubjectDao.AdminSubjectRow) {
        if (row.usageCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cannot delete")
                .setMessage(
                    "This subject is already used by deck records.\n\nDeactivate it instead so old decks stay safe."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete subject?")
            .setMessage("This will permanently delete \"${row.name}\".")
            .setPositiveButton("Delete") { _, _ ->
                val rows = subjectDao.deleteSubject(row.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Subject deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete subject", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class SubjectsAdapter(
        private val onEdit: (SubjectDao.AdminSubjectRow) -> Unit,
        private val onToggle: (SubjectDao.AdminSubjectRow) -> Unit,
        private val onDelete: (SubjectDao.AdminSubjectRow) -> Unit
    ) : ListAdapter<SubjectDao.AdminSubjectRow, SubjectsAdapter.VH>(Diff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSubjectBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onEdit, onToggle, onDelete)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val binding: ItemSubjectBinding,
            private val onEdit: (SubjectDao.AdminSubjectRow) -> Unit,
            private val onToggle: (SubjectDao.AdminSubjectRow) -> Unit,
            private val onDelete: (SubjectDao.AdminSubjectRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: SubjectDao.AdminSubjectRow) {
                binding.tvName.text = row.name
                binding.tvCategory.text = "Category: ${row.categoryName}"
                binding.tvDescription.text =
                    row.description?.takeIf { it.isNotBlank() } ?: "No description"

                binding.chipStatus.text = if (row.isActive) "Active" else "Inactive"
                binding.chipSort.text = "Order: ${row.sortOrder}"
                binding.chipUsage.text = "Used: ${row.usageCount}"

                binding.btnEdit.setOnClickListener { onEdit(row) }
                binding.btnToggle.text = if (row.isActive) "Deactivate" else "Activate"
                binding.btnToggle.setOnClickListener { onToggle(row) }

                binding.btnDelete.isEnabled = row.usageCount == 0
                binding.btnDelete.isEnabled = row.usageCount == 0
                binding.btnDelete.setOnClickListener { onDelete(row) }
            }
        }

        private class Diff : DiffUtil.ItemCallback<SubjectDao.AdminSubjectRow>() {
            override fun areItemsTheSame(
                oldItem: SubjectDao.AdminSubjectRow,
                newItem: SubjectDao.AdminSubjectRow
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: SubjectDao.AdminSubjectRow,
                newItem: SubjectDao.AdminSubjectRow
            ): Boolean = oldItem == newItem
        }
    }
}