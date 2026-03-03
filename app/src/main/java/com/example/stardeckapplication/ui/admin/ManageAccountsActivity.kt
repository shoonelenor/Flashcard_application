package com.example.stardeckapplication.ui.admin

import androidx.appcompat.app.AlertDialog
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.databinding.ActivityManageAccountsBinding
import com.example.stardeckapplication.databinding.DialogAdminUserBinding
import com.example.stardeckapplication.databinding.ItemAdminUserBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.random.Random

class ManageAccountsActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageAccountsBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var all: List<StarDeckDbHelper.SimpleUserRow> = emptyList()
    private var premiumIds: Set<Long> = emptySet()

    private var roleFilter: String? = null      // null = all
    private var statusFilter: String? = null    // null = all
    private var premiumOnly: Boolean = false

    private val adapter = UsersAdapter(
        premiumIds = { premiumIds },
        onEdit = { showEditDialog(it) },
        onResetPw = { resetPassword(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageAccountsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish(); return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Accounts"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        // Advanced filters
        b.chipRoleGroup.setOnCheckedStateChangeListener { _, _ ->
            roleFilter = when {
                b.chipRoleUser.isChecked -> DbContract.ROLE_USER
                b.chipRoleManager.isChecked -> DbContract.ROLE_MANAGER
                b.chipRoleAdmin.isChecked -> DbContract.ROLE_ADMIN
                else -> null
            }
            applyFilters()
        }

        b.chipStatusGroup.setOnCheckedStateChangeListener { _, _ ->
            statusFilter = when {
                b.chipStatusActive.isChecked -> DbContract.STATUS_ACTIVE
                b.chipStatusDisabled.isChecked -> DbContract.STATUS_DISABLED
                else -> null
            }
            applyFilters()
        }

        b.swPremiumOnly.setOnCheckedChangeListener { _, isChecked ->
            premiumOnly = isChecked
            applyFilters()
        }

        b.fabAdd.setOnClickListener { showCreateDialog() }

        if (!ensureDbReady()) return
        reload()
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

    private fun reload() {
        try {
            all = db.adminGetAllUsers()
            premiumIds = db.adminGetPremiumUserIds()
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

        // Text search
        if (q.isNotBlank()) {
            filtered = filtered.filter {
                it.name.lowercase().contains(q) || it.email.lowercase().contains(q)
            }
        }

        // Role filter
        roleFilter?.let { role ->
            filtered = filtered.filter { it.role == role }
        }

        // Status filter
        statusFilter?.let { st ->
            filtered = filtered.filter { it.status == st }
        }

        // Premium only filter
        if (premiumOnly) {
            filtered = filtered.filter { premiumIds.contains(it.id) }
        }

        adapter.submit(filtered)
        b.tvCount.text = "${filtered.size} account(s)"
        b.groupEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        b.recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val d = DialogAdminUserBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Account"
        d.etEmail.isEnabled = true
        d.groupTempPw.visibility = View.VISIBLE
        d.etTempPw.setText(generateTempPassword())

        d.chipUser.isChecked = true
        d.swDisabled.isChecked = false
        d.swPremium.isChecked = false

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilName.error = null
                d.tilEmail.error = null
                d.tilTempPw.error = null

                val name = d.etName.text?.toString().orEmpty().trim()
                val email = d.etEmail.text?.toString().orEmpty().trim().lowercase()
                val role = selectedRole(d)
                val disabled = d.swDisabled.isChecked
                val premium = d.swPremium.isChecked
                val tempPw = d.etTempPw.text?.toString().orEmpty()

                var ok = true
                if (name.isBlank()) { d.tilName.error = "Name required"; ok = false }
                if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    d.tilEmail.error = "Valid email required"; ok = false
                }
                if (tempPw.length < 8) { d.tilTempPw.error = "Min 8 characters"; ok = false }
                if (!ok) return@setOnClickListener

                try {
                    val newId = db.adminCreateStaff(
                        name = name,
                        email = email,
                        tempPassword = tempPw.toCharArray(),
                        role = role,
                        status = if (disabled) DbContract.STATUS_DISABLED else DbContract.STATUS_ACTIVE
                    )
                    db.setUserPremium(newId, premium)

                    Snackbar.make(b.root, "Account created", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Could not create: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun showEditDialog(u: StarDeckDbHelper.SimpleUserRow) {
        val d = DialogAdminUserBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Account"
        d.etName.setText(u.name)
        d.etEmail.setText(u.email)
        d.etEmail.isEnabled = false
        d.groupTempPw.visibility = View.GONE

        when (u.role) {
            DbContract.ROLE_ADMIN -> d.chipAdmin.isChecked = true
            DbContract.ROLE_MANAGER -> d.chipManager.isChecked = true
            else -> d.chipUser.isChecked = true
        }

        d.swDisabled.isChecked = (u.status == DbContract.STATUS_DISABLED)
        d.swPremium.isChecked = premiumIds.contains(u.id)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilName.error = null
                val name = d.etName.text?.toString().orEmpty().trim()
                if (name.isBlank()) { d.tilName.error = "Name required"; return@setOnClickListener }

                try {
                    db.adminUpdateUser(
                        id = u.id,
                        name = name,
                        role = selectedRole(d),
                        status = if (d.swDisabled.isChecked) DbContract.STATUS_DISABLED else DbContract.STATUS_ACTIVE
                    )
                    db.setUserPremium(u.id, d.swPremium.isChecked)

                    Snackbar.make(b.root, "Saved", Snackbar.LENGTH_SHORT).show()
                    reload()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Could not save: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun resetPassword(u: StarDeckDbHelper.SimpleUserRow) {
        val temp = generateTempPassword()
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset password?")
            .setMessage("Temp PW: $temp\n\nUser will be forced to change it at next login.")
            .setPositiveButton("Reset") { _, _ ->
                try {
                    db.adminResetPassword(u.id, temp.toCharArray())
                    Snackbar.make(b.root, "Password reset", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Reset failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectedRole(d: DialogAdminUserBinding): String {
        return when {
            d.chipAdmin.isChecked -> DbContract.ROLE_ADMIN
            d.chipManager.isChecked -> DbContract.ROLE_MANAGER
            else -> DbContract.ROLE_USER
        }
    }

    private fun generateTempPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val special = "!@#"
        val core = (1..9).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return core + special[Random.nextInt(special.length)]
    }

    private fun showDbFixDialog(e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Database refresh needed")
            .setMessage(
                "Because new features were added, the database schema changed.\n\n" +
                        "Fix (do once): uninstall the app OR clear app data.\n\nError: ${e.message}"
            )
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private class UsersAdapter(
        val premiumIds: () -> Set<Long>,
        val onEdit: (StarDeckDbHelper.SimpleUserRow) -> Unit,
        val onResetPw: (StarDeckDbHelper.SimpleUserRow) -> Unit
    ) : RecyclerView.Adapter<UsersAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.SimpleUserRow>()

        fun submit(newItems: List<StarDeckDbHelper.SimpleUserRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAdminUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, premiumIds, onEdit, onResetPw)
        }

        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        class VH(
            private val b: ItemAdminUserBinding,
            val premiumIds: () -> Set<Long>,
            val onEdit: (StarDeckDbHelper.SimpleUserRow) -> Unit,
            val onResetPw: (StarDeckDbHelper.SimpleUserRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(u: StarDeckDbHelper.SimpleUserRow) {
                b.tvName.text = u.name
                b.tvEmail.text = u.email
                b.chipRole.text = u.role
                b.chipStatus.text = u.status
                b.chipPremium.visibility = if (premiumIds().contains(u.id)) View.VISIBLE else View.GONE

                b.btnEdit.setOnClickListener { onEdit(u) }
                b.btnReset.setOnClickListener { onResetPw(u) }
            }
        }
    }

    private fun setDialogBusy(dialog: AlertDialog, busy: Boolean) {
        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = !busy
        btn.alpha = if (busy) 0.6f else 1f
    }
}