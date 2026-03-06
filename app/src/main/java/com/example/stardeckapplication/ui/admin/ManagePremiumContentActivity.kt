package com.example.stardeckapplication.ui.admin

import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManagePremiumContentBinding
import com.example.stardeckapplication.databinding.DialogAdminDeckContentBinding
import com.example.stardeckapplication.databinding.ItemAdminDeckContentBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ManagePremiumContentActivity : AppCompatActivity() {

    private lateinit var b: ActivityManagePremiumContentBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var all: List<StarDeckDbHelper.AdminDeckContentRow> = emptyList()
    private var statusFilter: String? = null
    private var premiumOnly: Boolean = false

    private val adapter = DeckContentAdapter(
        onEdit = { showEditDialog(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManagePremiumContentBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Premium Content Setup"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        b.chipStatusGroup.setOnCheckedStateChangeListener { _, _ ->
            statusFilter = when {
                b.chipStatusActive.isChecked -> DbContract.DECK_ACTIVE
                b.chipStatusHidden.isChecked -> DbContract.DECK_HIDDEN
                else -> null
            }
            applyFilters()
        }

        b.swPremiumOnly.setOnCheckedChangeListener { _, isChecked ->
            premiumOnly = isChecked
            applyFilters()
        }

        b.btnEnsureSeed.setOnClickListener {
            ensureSeedAndReload(showMessage = true)
        }

        if (!ensureDbReady()) return
        ensureSeedAndReload(showMessage = false)
    }

    override fun onResume() {
        super.onResume()
        if (ensureDbReady()) reload()
    }

    private fun ensureDbReady(): Boolean {
        return try {
            db.writableDatabase
            true
        } catch (e: Exception) {
            showDbFixDialog(e)
            false
        }
    }

    private fun ensureSeedAndReload(showMessage: Boolean) {
        try {
            val added = db.adminEnsurePremiumSeedContent()
            if (showMessage) {
                val msg = if (added > 0) {
                    "Added $added premium deck(s)"
                } else {
                    "Premium seed content already exists"
                }
                Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
            }
            reload()
        } catch (e: Exception) {
            Snackbar.make(b.root, "Could not seed content: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun reload() {
        try {
            all = db.adminGetAllDeckContent()
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

        if (q.isNotBlank()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                        (it.description ?: "").lowercase().contains(q) ||
                        it.ownerName.lowercase().contains(q) ||
                        it.ownerEmail.lowercase().contains(q)
            }
        }

        statusFilter?.let { status ->
            filtered = filtered.filter { it.status == status }
        }

        if (premiumOnly) {
            filtered = filtered.filter { it.isPremium }
        }

        adapter.submit(filtered)
        b.tvCount.text = "${filtered.size} deck(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showEditDialog(row: StarDeckDbHelper.AdminDeckContentRow) {
        val d = DialogAdminDeckContentBinding.inflate(layoutInflater)

        d.tvOwner.text = "Owner: ${row.ownerName} (${row.ownerEmail})"
        d.etTitle.setText(row.title)
        d.etDescription.setText(row.description.orEmpty())
        d.swPremium.isChecked = row.isPremium
        d.swHidden.isChecked = (row.status == DbContract.DECK_HIDDEN)

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

                try {
                    val rows = db.adminUpdateDeckContent(
                        deckId = row.id,
                        title = title,
                        description = description,
                        isPremium = isPremium,
                        isHidden = isHidden
                    )

                    if (rows == 1) {
                        Snackbar.make(b.root, "Premium content updated", Snackbar.LENGTH_SHORT).show()
                        reload()
                        dialog.dismiss()
                    } else {
                        Snackbar.make(b.root, "Could not update deck", Snackbar.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Could not save: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showDbFixDialog(e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Database refresh needed")
            .setMessage(
                "Because new admin content features were added, the database state may need refresh.\n\n" +
                        "Fix once: uninstall the app OR clear app data.\n\nError: ${e.message}"
            )
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private class DeckContentAdapter(
        private val onEdit: (StarDeckDbHelper.AdminDeckContentRow) -> Unit
    ) : RecyclerView.Adapter<DeckContentAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.AdminDeckContentRow>()

        fun submit(newItems: List<StarDeckDbHelper.AdminDeckContentRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAdminDeckContentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onEdit)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b: ItemAdminDeckContentBinding,
            private val onEdit: (StarDeckDbHelper.AdminDeckContentRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: StarDeckDbHelper.AdminDeckContentRow) {
                b.tvTitle.text = row.title
                b.tvOwner.text = "${row.ownerName} • ${row.ownerEmail}"
                b.tvDesc.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.tvCardCount.text = if (row.cardCount == 1) "1 card" else "${row.cardCount} cards"
                b.chipPremium.visibility = if (row.isPremium) View.VISIBLE else View.GONE
                b.chipStatus.text = if (row.status == DbContract.DECK_HIDDEN) "Hidden" else "Active"

                b.btnEdit.setOnClickListener { onEdit(row) }
                b.root.setOnClickListener { onEdit(row) }
            }
        }
    }
}