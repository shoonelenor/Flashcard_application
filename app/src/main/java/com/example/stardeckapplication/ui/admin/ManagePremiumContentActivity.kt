package com.example.stardeckapplication.ui.admin

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.setPadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManagePremiumContentBinding
import com.example.stardeckapplication.databinding.ItemAdminDeckContentBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.manager.ManagerDeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ManagePremiumContentActivity : AppCompatActivity() {

    private lateinit var b: ActivityManagePremiumContentBinding

    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var adminId: Long = -1L
    private var all: List<StarDeckDbHelper.AdminDeckContentRow> = emptyList()
    private var statusFilter: String? = null
    private var premiumOnly: Boolean = false

    private val adapter = DeckContentAdapter(
        onAction = { showDeckActions(it) }
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
        adminId = me.id

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Content Setup"

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

        b.swPremiumOnly.setOnCheckedChangeListener { _, checked ->
            premiumOnly = checked
            applyFilters()
        }

        b.btnEnsureSeed.setOnClickListener {
            ensureSeedAndReload(showMessage = true)
        }

        if (!ensureDbReady()) return
        reload()
    }

    override fun onResume() {
        super.onResume()
        if (this::b.isInitialized && ensureDbReady()) {
            reload()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1001, 0, "Create Deck")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1001 -> {
                showDeckForm(null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                    "Added $added seeded deck(s)"
                } else {
                    "Seed content already exists"
                }
                Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
            }
            reload()
        } catch (e: Exception) {
            Snackbar.make(b.root, "Seed failed: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun reload() {
        try {
            all = db.adminGetOwnDeckContent(adminId)
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
                        (it.description ?: "").lowercase().contains(q)
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
        val empty = filtered.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showDeckActions(row: StarDeckDbHelper.AdminDeckContentRow) {
        val items = arrayOf(
            "Edit deck",
            "Manage cards",
            "Delete deck"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(row.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showDeckForm(row)
                    1 -> openCards(row)
                    2 -> confirmDeleteDeck(row)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCards(row: StarDeckDbHelper.AdminDeckContentRow) {
        startActivity(
            Intent(this, ManagerDeckCardsActivity::class.java).apply {
                putExtra(ManagerDeckCardsActivity.EXTRA_DECK_ID, row.id)
                putExtra(ManagerDeckCardsActivity.EXTRA_DECK_TITLE, row.title)
                putExtra(ManagerDeckCardsActivity.EXTRA_OWNER_EMAIL, row.ownerEmail)
                putExtra(ManagerDeckCardsActivity.EXTRA_ADMIN_EDIT_MODE, true)
            }
        )
    }

    private fun confirmDeleteDeck(row: StarDeckDbHelper.AdminDeckContentRow) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete deck?")
            .setMessage(
                "Deck: ${row.title}\n\nThis will also delete all cards inside this deck."
            )
            .setPositiveButton("Delete") { _, _ ->
                val rows = db.adminDeleteDeckContentForAdmin(adminId, row.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Deck deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete deck", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeckForm(existing: StarDeckDbHelper.AdminDeckContentRow?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20))
        }

        val tilTitle = TextInputLayout(this).apply {
            hint = "Deck title"
        }
        val etTitle = TextInputEditText(this)
        tilTitle.addView(etTitle)

        val tilDescription = TextInputLayout(this).apply {
            hint = "Description"
        }
        val etDescription = TextInputEditText(this).apply {
            minLines = 2
            maxLines = 4
        }
        tilDescription.addView(etDescription)

        val swPremium = SwitchCompat(this).apply {
            text = "Premium deck"
        }
        val swPublic = SwitchCompat(this).apply {
            text = "Public deck"
        }
        val swHidden = SwitchCompat(this).apply {
            text = "Hidden deck"
        }

        container.addView(tilTitle)
        container.addView(tilDescription)
        container.addView(swPremium)
        container.addView(swPublic)
        container.addView(swHidden)

        if (existing != null) {
            etTitle.setText(existing.title)
            etDescription.setText(existing.description.orEmpty())
            swPremium.isChecked = existing.isPremium
            swPublic.isChecked = existing.isPublic
            swHidden.isChecked = existing.status == DbContract.DECK_HIDDEN
        } else {
            swPublic.isChecked = true
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Create deck" else "Edit deck")
            .setView(container)
            .setPositiveButton(if (existing == null) "Create" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                tilTitle.error = null
                tilDescription.error = null

                val title = etTitle.text?.toString().orEmpty().trim()
                val description = etDescription.text?.toString()?.trim()
                val isPremium = swPremium.isChecked
                val isPublic = swPublic.isChecked
                val isHidden = swHidden.isChecked

                if (title.isBlank()) {
                    tilTitle.error = "Title is required"
                    return@setOnClickListener
                }

                if (title.length > 60) {
                    tilTitle.error = "Max 60 characters"
                    return@setOnClickListener
                }

                if (!description.isNullOrBlank() && description.length > 250) {
                    tilDescription.error = "Max 250 characters"
                    return@setOnClickListener
                }

                try {
                    if (existing == null) {
                        val id = db.adminCreateDeckContentForAdmin(
                            adminUserId = adminId,
                            title = title,
                            description = description,
                            isPremium = isPremium,
                            isPublic = isPublic,
                            isHidden = isHidden
                        )
                        if (id > 0) {
                            Snackbar.make(b.root, "Deck created", Snackbar.LENGTH_SHORT).show()
                            reload()
                            dialog.dismiss()
                        } else {
                            Snackbar.make(b.root, "Could not create deck", Snackbar.LENGTH_LONG).show()
                        }
                    } else {
                        val rows = db.adminUpdateDeckContentForAdmin(
                            adminUserId = adminId,
                            deckId = existing.id,
                            title = title,
                            description = description,
                            isPremium = isPremium,
                            isPublic = isPublic,
                            isHidden = isHidden
                        )
                        if (rows == 1) {
                            Snackbar.make(b.root, "Deck updated", Snackbar.LENGTH_SHORT).show()
                            reload()
                            dialog.dismiss()
                        } else {
                            Snackbar.make(b.root, "Could not update deck", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Save failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showDbFixDialog(e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Database refresh needed")
            .setMessage(
                "Because deck visibility and premium logic changed, clear app data or reinstall once.\n\nError: ${e.message}"
            )
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private class DeckContentAdapter(
        private val onAction: (StarDeckDbHelper.AdminDeckContentRow) -> Unit
    ) : RecyclerView.Adapter<DeckContentAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.AdminDeckContentRow>()

        fun submit(newItems: List<StarDeckDbHelper.AdminDeckContentRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val binding = ItemAdminDeckContentBinding.inflate(
                layoutInflater(parent),
                parent,
                false
            )
            return VH(binding, onAction)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b: ItemAdminDeckContentBinding,
            private val onAction: (StarDeckDbHelper.AdminDeckContentRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: StarDeckDbHelper.AdminDeckContentRow) {
                b.tvTitle.text = row.title
                b.tvOwner.text = row.ownerName
                b.tvDesc.text = row.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.tvCardCount.text = if (row.cardCount == 1) "1 card" else "${row.cardCount} cards"

                b.chipPremium.visibility = if (row.isPremium) View.VISIBLE else View.GONE
                b.chipStatus.text = buildString {
                    append(if (row.status == DbContract.DECK_HIDDEN) "Hidden" else "Active")
                    append(" • ")
                    append(if (row.isPublic) "Public" else "Private")
                }

                b.btnEdit.setOnClickListener { onAction(row) }
                b.root.setOnClickListener { onAction(row) }
            }
        }

        companion object {
            private fun layoutInflater(parent: android.view.ViewGroup) =
                android.view.LayoutInflater.from(parent.context)
        }
    }
}