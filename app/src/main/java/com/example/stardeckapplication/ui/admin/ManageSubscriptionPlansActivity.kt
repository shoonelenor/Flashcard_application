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
import com.example.stardeckapplication.databinding.ActivityManageSubscriptionPlansBinding
import com.example.stardeckapplication.databinding.DialogSubscriptionPlanBinding
import com.example.stardeckapplication.databinding.ItemSubscriptionPlanBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.SubscriptionPlanDao
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManageSubscriptionPlansActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageSubscriptionPlansBinding

    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val subscriptionDao by lazy { SubscriptionPlanDao(dbHelper) }
    private val session by lazy { SessionManager(this) }

    private var all: List<SubscriptionPlanDao.AdminPlanRow> = emptyList()
    private var statusFilter: Boolean? = null

    private val adapter = PlansAdapter(
        onEdit = { showEditDialog(it) },
        onToggle = { confirmToggle(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageSubscriptionPlansBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        subscriptionDao.ensureDefaultsInserted()

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Subscription Plan Setup"
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
        all = subscriptionDao.adminGetAllPlans()
        applyFilters()
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var filtered = all
        if (q.isNotBlank()) {
            filtered = filtered.filter { row ->
                row.name.lowercase().contains(q) ||
                        row.billingLabel.lowercase().contains(q) ||
                        row.priceText.lowercase().contains(q) ||
                        row.description.orEmpty().lowercase().contains(q)
            }
        }

        statusFilter?.let { activeOnly ->
            filtered = filtered.filter { it.isActive == activeOnly }
        }

        adapter.submitList(filtered)
        b.tvCount.text = "${filtered.size} plan(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val d = DialogSubscriptionPlanBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Subscription Plan"
        d.swActive.isChecked = true
        d.etSortOrder.setText(subscriptionDao.getNextSortOrder().toString())
        val cycles = bindCycleDropdown(d, null)
        createPlanDialog(d, null, cycles).show()
    }

    private fun showEditDialog(row: SubscriptionPlanDao.AdminPlanRow) {
        val d = DialogSubscriptionPlanBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Subscription Plan"
        d.etName.setText(row.name)
        d.etPriceText.setText(row.priceText)
        d.etDurationDays.setText(row.durationDays.toString())
        d.etDescription.setText(row.description.orEmpty())
        d.etSortOrder.setText(row.sortOrder.toString())
        d.swActive.isChecked = row.isActive
        val cycles = bindCycleDropdown(d, row.billingCycle)
        createPlanDialog(d, row, cycles).show()
    }

    private fun bindCycleDropdown(
        d: DialogSubscriptionPlanBinding,
        selectedCycle: String?
    ): List<SubscriptionPlanDao.BillingCycleOption> {
        val options = subscriptionDao.getBillingCycleOptions()
        val labels = options.map { it.label }

        d.actBillingCycle.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        d.actBillingCycle.setOnClickListener { d.actBillingCycle.showDropDown() }

        val selected = options.firstOrNull { it.key == selectedCycle }
        d.actBillingCycle.setText(selected?.label ?: options.first().label, false)

        return options
    }

    private fun createPlanDialog(
        d: DialogSubscriptionPlanBinding,
        existing: SubscriptionPlanDao.AdminPlanRow?,
        cycles: List<SubscriptionPlanDao.BillingCycleOption>
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
                d.tilBillingCycle.error = null
                d.tilPriceText.error = null
                d.tilDurationDays.error = null
                d.tilDescription.error = null
                d.tilSortOrder.error = null

                val name = d.etName.text?.toString().orEmpty().trim()
                val billingCycle = resolveCycleKey(d.actBillingCycle.text?.toString().orEmpty(), cycles)
                val priceText = d.etPriceText.text?.toString().orEmpty().trim()
                val durationDays = d.etDurationDays.text?.toString().orEmpty().trim().toIntOrNull()
                val description = d.etDescription.text?.toString().orEmpty().trim()
                val sortOrder = d.etSortOrder.text?.toString().orEmpty().trim().toIntOrNull()
                val isActive = d.swActive.isChecked

                var ok = true

                if (name.length < 2) {
                    d.tilName.error = "At least 2 characters required"
                    ok = false
                } else if (name.length > 60) {
                    d.tilName.error = "Max 60 characters"
                    ok = false
                } else if (subscriptionDao.isNameTaken(name, existing?.id)) {
                    d.tilName.error = "Plan already exists"
                    ok = false
                }

                if (billingCycle == null) {
                    d.tilBillingCycle.error = "Choose monthly or yearly"
                    ok = false
                }

                if (priceText.isBlank()) {
                    d.tilPriceText.error = "Price text is required"
                    ok = false
                } else if (priceText.length > 40) {
                    d.tilPriceText.error = "Max 40 characters"
                    ok = false
                }

                if (durationDays == null || durationDays <= 0) {
                    d.tilDurationDays.error = "Enter 1 or higher"
                    ok = false
                }

                if (description.length > 200) {
                    d.tilDescription.error = "Max 200 characters"
                    ok = false
                }

                if (sortOrder == null || sortOrder < 0) {
                    d.tilSortOrder.error = "Enter 0 or higher"
                    ok = false
                }

                if (!ok || billingCycle == null) return@setOnClickListener

                try {
                    if (existing == null) {
                        subscriptionDao.createPlan(
                            name = name,
                            billingCycle = billingCycle,
                            priceText = priceText,
                            durationDays = durationDays!!,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder!!
                        )
                        Snackbar.make(b.root, "Plan created", Snackbar.LENGTH_SHORT).show()
                    } else {
                        subscriptionDao.updatePlan(
                            planId = existing.id,
                            name = name,
                            billingCycle = billingCycle,
                            priceText = priceText,
                            durationDays = durationDays!!,
                            description = description.ifBlank { null },
                            isActive = isActive,
                            sortOrder = sortOrder!!
                        )
                        Snackbar.make(b.root, "Plan updated", Snackbar.LENGTH_SHORT).show()
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

    private fun resolveCycleKey(
        rawText: String,
        options: List<SubscriptionPlanDao.BillingCycleOption>
    ): String? {
        val clean = rawText.trim()
        return options.firstOrNull { it.label.equals(clean, ignoreCase = true) }?.key
    }

    private fun confirmToggle(row: SubscriptionPlanDao.AdminPlanRow) {
        val nextActive = !row.isActive
        MaterialAlertDialogBuilder(this)
            .setTitle("${if (nextActive) "Activate" else "Deactivate"} plan?")
            .setMessage("This will ${if (nextActive) "activate" else "deactivate"} \"${row.name}\".")
            .setPositiveButton("Yes") { _, _ ->
                subscriptionDao.setPlanActive(row.id, nextActive)
                Snackbar.make(
                    b.root,
                    "Plan ${if (nextActive) "activated" else "deactivated"}",
                    Snackbar.LENGTH_SHORT
                ).show()
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(row: SubscriptionPlanDao.AdminPlanRow) {
        if (row.subscriberCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cannot delete")
                .setMessage(
                    "This plan has already been used by users.\n\nDeactivate it instead so subscription history stays safe."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete plan?")
            .setMessage("This will permanently delete \"${row.name}\".")
            .setPositiveButton("Delete") { _, _ ->
                val rows = subscriptionDao.deletePlan(row.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Plan deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete plan", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class PlansAdapter(
        private val onEdit: (SubscriptionPlanDao.AdminPlanRow) -> Unit,
        private val onToggle: (SubscriptionPlanDao.AdminPlanRow) -> Unit,
        private val onDelete: (SubscriptionPlanDao.AdminPlanRow) -> Unit
    ) : ListAdapter<SubscriptionPlanDao.AdminPlanRow, PlansAdapter.VH>(Diff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSubscriptionPlanBinding.inflate(
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
            private val binding: ItemSubscriptionPlanBinding,
            private val onEdit: (SubscriptionPlanDao.AdminPlanRow) -> Unit,
            private val onToggle: (SubscriptionPlanDao.AdminPlanRow) -> Unit,
            private val onDelete: (SubscriptionPlanDao.AdminPlanRow) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(row: SubscriptionPlanDao.AdminPlanRow) {
                binding.tvName.text = row.name
                binding.tvCycle.text = "${row.billingLabel} • ${row.priceText}"
                binding.tvDescription.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                binding.chipStatus.text = if (row.isActive) "Active" else "Inactive"
                binding.chipDuration.text = "${row.durationDays} day(s)"
                binding.chipSubscribers.text = "Users: ${row.subscriberCount}"
                binding.chipSort.text = "Order: ${row.sortOrder}"

                binding.btnEdit.setOnClickListener { onEdit(row) }
                binding.btnToggle.text = if (row.isActive) "Deactivate" else "Activate"
                binding.btnToggle.setOnClickListener { onToggle(row) }
                binding.btnDelete.isEnabled = row.subscriberCount == 0
                binding.btnDelete.setOnClickListener { onDelete(row) }
            }
        }

        private class Diff : DiffUtil.ItemCallback<SubscriptionPlanDao.AdminPlanRow>() {
            override fun areItemsTheSame(
                oldItem: SubscriptionPlanDao.AdminPlanRow,
                newItem: SubscriptionPlanDao.AdminPlanRow
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: SubscriptionPlanDao.AdminPlanRow,
                newItem: SubscriptionPlanDao.AdminPlanRow
            ): Boolean = oldItem == newItem
        }
    }
}