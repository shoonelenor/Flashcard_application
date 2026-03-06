package com.example.stardeckapplication.ui.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.stardeckapplication.databinding.ActivityManageContentSetupBinding
import com.example.stardeckapplication.databinding.DialogAdminContentSetupBinding
import com.example.stardeckapplication.databinding.ItemAdminContentDeckBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManageContentSetupActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageContentSetupBinding
    private val session by lazy { SessionManager(this) }
    private val db by lazy { StarDeckDbHelper(this) }

    private var all: List<StarDeckDbHelper.AdminDeckContentRow> = emptyList()
    private var premiumOnly = false
    private var statusFilter: String? = null

    private val adapter = DeckAdapter(
        onEdit = { row -> showEditDialog(row) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageContentSetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Content Setup"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged {
            applyFilters()
        }

        b.swPremiumOnly.setOnCheckedChangeListener { _, checked ->
            premiumOnly = checked
            applyFilters()
        }

        b.chipStatusGroup.setOnCheckedStateChangeListener { _, _ ->
            statusFilter = when {
                b.chipActive.isChecked -> DbContract.DECK_ACTIVE
                b.chipHidden.isChecked -> DbContract.DECK_HIDDEN
                else -> null
            }
            applyFilters()
        }

        b.btnSeedPremium.setOnClickListener {
            val added = runCatching { db.adminEnsurePremiumSeedContent() }.getOrDefault(0)
            val message = if (added > 0) {
                "Added $added premium deck(s)"
            } else {
                "Premium seed content already exists"
            }
            Snackbar.make(b.root, message, Snackbar.LENGTH_SHORT).show()
            reload()
        }

        db.adminEnsurePremiumSeedContent()
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        all = runCatching { db.adminGetAllDeckContent() }.getOrDefault(emptyList())
        applyFilters()
    }

    private fun applyFilters() {
        val q = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var list = all

        if (q.isNotBlank()) {
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        (it.description ?: "").lowercase().contains(q) ||
                        it.ownerName.lowercase().contains(q) ||
                        it.ownerEmail.lowercase().contains(q)
            }
        }

        if (premiumOnly) {
            list = list.filter { it.isPremium }
        }

        statusFilter?.let { status ->
            list = list.filter { it.status == status }
        }

        adapter.submit(list)
        b.tvCount.text = "${list.size} deck(s)"

        val empty = list.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showEditDialog(row: StarDeckDbHelper.AdminDeckContentRow) {
        val d = DialogAdminContentSetupBinding.inflate(layoutInflater)

        d.tvOwner.text = "Owner: ${row.ownerName} (${row.ownerEmail})"
        d.etTitle.setText(row.title)
        d.etDescription.setText(row.description.orEmpty())
        d.swPremium.isChecked = row.isPremium
        d.swHidden.isChecked = row.status == DbContract.DECK_HIDDEN

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilTitle.error = null
                d.tilDescription.error = null

                val title = d.etTitle.text?.toString().orEmpty().trim()
                val description = d.etDescription.text?.toString()?.trim()
                val isPremium = d.swPremium.isChecked
                val isHidden = d.swHidden.isChecked

                if (title.isBlank()) {
                    d.tilTitle.error = "Title is required"
                    return@setOnClickListener
                }

                if (title.length > 60) {
                    d.tilTitle.error = "Max 60 characters"
                    return@setOnClickListener
                }

                if (!description.isNullOrBlank() && description.length > 250) {
                    d.tilDescription.error = "Max 250 characters"
                    return@setOnClickListener
                }

                val rows = runCatching {
                    db.adminUpdateDeckContent(
                        deckId = row.id,
                        title = title,
                        description = description,
                        isPremium = isPremium,
                        isHidden = isHidden
                    )
                }.getOrDefault(0)

                if (rows == 1) {
                    Snackbar.make(b.root, "Content updated", Snackbar.LENGTH_SHORT).show()
                    dialog.dismiss()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not update content", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private class DeckAdapter(
        private val onEdit: (StarDeckDbHelper.AdminDeckContentRow) -> Unit
    ) : RecyclerView.Adapter<DeckAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.AdminDeckContentRow>()

        fun submit(list: List<StarDeckDbHelper.AdminDeckContentRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAdminContentDeckBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onEdit)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class VH(
            private val b: ItemAdminContentDeckBinding,
            private val onEdit: (StarDeckDbHelper.AdminDeckContentRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: StarDeckDbHelper.AdminDeckContentRow) {
                b.tvTitle.text = row.title
                b.tvOwner.text = "${row.ownerName} • ${row.ownerEmail}"
                b.tvDescription.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.tvCards.text = if (row.cardCount == 1) "1 card" else "${row.cardCount} cards"

                b.chipStatus.text = if (row.status == DbContract.DECK_HIDDEN) "Hidden" else "Active"
                b.chipPremium.visibility = if (row.isPremium) View.VISIBLE else View.GONE

                b.btnEdit.setOnClickListener { onEdit(row) }
                b.root.setOnClickListener { onEdit(row) }
            }
        }
    }
}