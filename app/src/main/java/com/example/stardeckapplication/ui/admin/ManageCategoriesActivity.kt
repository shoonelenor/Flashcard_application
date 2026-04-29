package com.example.stardeckapplication.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManageCategoriesBinding
import com.example.stardeckapplication.databinding.DialogCategoryBinding
import com.example.stardeckapplication.databinding.ItemCategoryBinding
import com.example.stardeckapplication.db.CategoryDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManageCategoriesActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageCategoriesBinding

    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val categoryDao by lazy { CategoryDao(dbHelper) }
    private val session by lazy { SessionManager(this) }

    private var all: List<CategoryDao.AdminCategoryRow> = emptyList()
    private var statusFilter: Boolean? = null

    private val adapter = CategoriesAdapter(
        onEdit = { showEditDialog(it) },
        onToggle = { confirmToggle(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageCategoriesBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Category Setup"
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
        all = categoryDao.adminGetAllCategories()
        applyFilters()
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = all

        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.name.lowercase().contains(q) ||
                        row.description.orEmpty().lowercase().contains(q)
            }
        }

        statusFilter?.let { activeOnly ->
            filtered = filtered.filter { it.isActive == activeOnly }
        }

        adapter.submitList(filtered)
        b.tvCount.text = "${filtered.size} category(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val d = DialogCategoryBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Category"
        d.swActive.isChecked = true
        d.etSortOrder.setText(categoryDao.getNextSortOrder().toString())

        createCategoryDialog(d, existing = null).show()
    }

    private fun showEditDialog(row: CategoryDao.AdminCategoryRow) {
        val d = DialogCategoryBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Category"
        d.etName.setText(row.name)
        d.etDescription.setText(row.description.orEmpty())
        d.etSortOrder.setText(row.sortOrder.toString())
        d.swActive.isChecked = row.isActive

        createCategoryDialog(d, existing = row).show()
    }

    private fun createCategoryDialog(
        d: DialogCategoryBinding,
        existing: CategoryDao.AdminCategoryRow?
    ): AlertDialog {
        val positiveText = if (existing == null) "Create" else "Save"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton(positiveText, null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilName.error = null
                d.tilDescription.error = null
                d.tilSortOrder.error = null

                val name = d.etName.text?.toString().orEmpty().trim()
                val description = d.etDescription.text?.toString().orEmpty().trim()
                val parsedSortOrder = d.etSortOrder.text?.toString().orEmpty().trim().toIntOrNull()
                val isActive = d.swActive.isChecked

                var ok = true

                if (name.length < 2) {
                    d.tilName.error = "At least 2 characters required"
                    ok = false
                } else if (name.length > 60) {
                    d.tilName.error = "Max 60 characters"
                    ok = false
                } else if (categoryDao.isNameTaken(name, existing?.id)) {
                    d.tilName.error = "Category already exists"
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
                        categoryDao.createCategory(
                            name = name,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder
                        )
                        Snackbar.make(b.root, "Category created", Snackbar.LENGTH_SHORT).show()
                    } else {
                        categoryDao.updateCategory(
                            categoryId = existing.id,
                            name = name,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder
                        )
                        Snackbar.make(b.root, "Category updated", Snackbar.LENGTH_SHORT).show()
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

    private fun confirmToggle(row: CategoryDao.AdminCategoryRow) {
        val nextActive = !row.isActive

        MaterialAlertDialogBuilder(this)
            .setTitle("${if (nextActive) "Activate" else "Deactivate"} category?")
            .setMessage("This will ${if (nextActive) "activate" else "deactivate"} \"${row.name}\".")
            .setPositiveButton("Yes") { _, _ ->
                categoryDao.setCategoryActive(row.id, nextActive)
                Snackbar.make(
                    b.root,
                    "Category ${if (nextActive) "activated" else "deactivated"}",
                    Snackbar.LENGTH_SHORT
                ).show()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(row: CategoryDao.AdminCategoryRow) {
        if (row.usageCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cannot delete")
                .setMessage(
                    "This category is already used by deck records.\n\n" +
                            "Deactivate it instead so old decks stay safe."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete category?")
            .setMessage("This will permanently delete \"${row.name}\".")
            .setPositiveButton("Delete") { _, _ ->
                val rows = categoryDao.deleteCategory(row.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Category deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete category", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class CategoriesAdapter(
        private val onEdit: (CategoryDao.AdminCategoryRow) -> Unit,
        private val onToggle: (CategoryDao.AdminCategoryRow) -> Unit,
        private val onDelete: (CategoryDao.AdminCategoryRow) -> Unit
    ) : ListAdapter<CategoryDao.AdminCategoryRow, CategoriesAdapter.VH>(Diff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemCategoryBinding.inflate(
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
            private val binding: ItemCategoryBinding,
            private val onEdit: (CategoryDao.AdminCategoryRow) -> Unit,
            private val onToggle: (CategoryDao.AdminCategoryRow) -> Unit,
            private val onDelete: (CategoryDao.AdminCategoryRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: CategoryDao.AdminCategoryRow) {
                binding.tvName.text = row.name
                binding.tvDescription.text =
                    row.description?.takeIf { it.isNotBlank() } ?: "No description"

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

        private class Diff : DiffUtil.ItemCallback<CategoryDao.AdminCategoryRow>() {
            override fun areItemsTheSame(
                oldItem: CategoryDao.AdminCategoryRow,
                newItem: CategoryDao.AdminCategoryRow
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: CategoryDao.AdminCategoryRow,
                newItem: CategoryDao.AdminCategoryRow
            ): Boolean = oldItem == newItem
        }
    }
}