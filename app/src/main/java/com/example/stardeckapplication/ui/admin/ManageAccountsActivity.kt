package com.example.stardeckapplication.ui.admin

import android.database.sqlite.SQLiteException
import android.graphics.Color
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
import com.example.stardeckapplication.util.PasswordHasher
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.random.Random

class ManageAccountsActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageAccountsBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private var all: List<SimpleUserRow> = emptyList()
    private var premiumIds: Set<Long> = emptySet()

    private var roleFilter: String? = null
    private var statusFilter: String? = null
    private var premiumOnly: Boolean = false

    // Avatar background colors — cycles through for variety
    private val avatarColors = listOf(
        0xFF4F6BFF.toInt(), // stardeck_primary blue
        0xFF3A8DFF.toInt(), // teal_700 blue
        0xFF34D399.toInt(), // stardeck_success green
        0xFFFF5C7A.toInt(), // stardeck_error pink
        0xFFFBBF24.toInt(), // stardeck_gold amber
        0xFF7B91FF.toInt(), // stardeck_primary_light
        0xFF38BDF8.toInt()  // stardeck_info sky
    )

    private val adapter = UsersAdapter(
        premiumIds = { premiumIds },
        avatarColors = avatarColors,
        onEdit = { showEditDialog(it) },
        onResetPw = { resetPassword(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageAccountsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Accounts"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged { applyFilters() }

        // Role filter — plain TextViews acting as chips
        listOf(b.chipRoleAll, b.chipRoleUser, b.chipRoleManager, b.chipRoleAdmin).forEachIndexed { index, tv ->
            tv.setOnClickListener {
                roleFilter = when (index) {
                    1 -> DbContract.ROLE_USER
                    2 -> DbContract.ROLE_MANAGER
                    3 -> DbContract.ROLE_ADMIN
                    else -> null
                }
                applyFilters()
            }
        }

        // Status filter — plain TextViews acting as chips
        listOf(b.chipStatusAll, b.chipStatusActive, b.chipStatusDisabled).forEachIndexed { index, tv ->
            tv.setOnClickListener {
                statusFilter = when (index) {
                    1 -> DbContract.STATUS_ACTIVE
                    2 -> DbContract.STATUS_DISABLED
                    else -> null
                }
                applyFilters()
            }
        }

        b.swPremiumOnly.setOnCheckedChangeListener { _, checked ->
            premiumOnly = checked
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
            all = adminGetAllUsers()
            premiumIds = adminGetPremiumUserIds()
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
                it.name.lowercase().contains(q) || it.email.lowercase().contains(q)
            }
        }

        roleFilter?.let { role -> filtered = filtered.filter { it.role == role } }
        statusFilter?.let { st -> filtered = filtered.filter { it.status == st } }
        if (premiumOnly) filtered = filtered.filter { premiumIds.contains(it.id) }

        adapter.submit(filtered)
        b.tvCount.text = "${filtered.size} account(s)"
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
                    val newId = adminCreateStaff(
                        name = name, email = email,
                        tempPassword = tempPw.toCharArray(),
                        role = role,
                        status = if (disabled) DbContract.STATUS_DISABLED else DbContract.STATUS_ACTIVE
                    )
                    setUserPremium(newId, premium)
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

    private fun showEditDialog(u: SimpleUserRow) {
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
                    adminUpdateUser(
                        id = u.id, name = name,
                        role = selectedRole(d),
                        status = if (d.swDisabled.isChecked) DbContract.STATUS_DISABLED else DbContract.STATUS_ACTIVE
                    )
                    setUserPremium(u.id, d.swPremium.isChecked)
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

    private fun resetPassword(u: SimpleUserRow) {
        val temp = generateTempPassword()
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset password?")
            .setMessage("Temp PW: $temp\n\nUser will be forced to change it at next login.")
            .setPositiveButton("Reset") { _, _ ->
                try {
                    adminResetPassword(u.id, temp.toCharArray())
                    Snackbar.make(b.root, "Password reset", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(b.root, "Reset failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDelete(u: SimpleUserRow) {
        val me = session.load() ?: return
        if (u.id == me.id) {
            Snackbar.make(b.root, "You can't delete your own admin account.", Snackbar.LENGTH_LONG).show()
            return
        }

        val deps = adminGetUserDependencies(u.id)
        if (deps.deckCount > 0 || deps.studyCount > 0 || deps.reportCount > 0 || deps.progressCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete blocked")
                .setMessage(
                    "This account has related data.\n\n" +
                    "Decks: ${deps.deckCount}\nStudy sessions: ${deps.studyCount}\n" +
                    "Reports: ${deps.reportCount}\nCard progress: ${deps.progressCount}\n\n" +
                    "Use Disable instead of Delete."
                )
                .setPositiveButton("OK", null).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete account?")
            .setMessage("“${u.name}” will be permanently deleted. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val rows = adminDeleteUserIfSafe(me.id, u.id)
                if (rows == 1) {
                    Snackbar.make(b.root, "Account deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete account", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun selectedRole(d: DialogAdminUserBinding): String = when {
        d.chipAdmin.isChecked -> DbContract.ROLE_ADMIN
        d.chipManager.isChecked -> DbContract.ROLE_MANAGER
        else -> DbContract.ROLE_USER
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
            .setCancelable(false).show()
    }

    // ─── DB Models ─────────────────────────────────────────────────────────────

    private data class SimpleUserRow(
        val id: Long, val name: String, val email: String,
        val role: String, val status: String
    )

    private data class AdminUserDependencyRow(
        val deckCount: Int, val studyCount: Int,
        val reportCount: Int, val progressCount: Int
    )

    // ─── DB Helpers ────────────────────────────────────────────────────────────

    private fun adminGetAllUsers(): List<SimpleUserRow> {
        val sql = """
            SELECT ${DbContract.UID}, ${DbContract.UNAME}, ${DbContract.UEMAIL},
                   ${DbContract.UROLE}, ${DbContract.USTATUS}
            FROM ${DbContract.TUSERS}
            ORDER BY ${DbContract.UCREATEDAT} DESC
        """.trimIndent()
        val out = mutableListOf<SimpleUserRow>()
        db.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += SimpleUserRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
            }
        }
        return out
    }

    private fun adminGetPremiumUserIds(): Set<Long> {
        val sql = "SELECT ${DbContract.UID} FROM ${DbContract.TUSERS} WHERE ${DbContract.UISPREMIUMUSER}=1"
        val out = mutableSetOf<Long>()
        db.readableDatabase.rawQuery(sql, null).use { c -> while (c.moveToNext()) out += c.getLong(0) }
        return out
    }

    private fun adminCreateStaff(name: String, email: String, tempPassword: CharArray, role: String, status: String): Long {
        val cv = android.content.ContentValues().apply {
            put(DbContract.UNAME, name.trim())
            put(DbContract.UEMAIL, email.trim().lowercase())
            put(DbContract.UPASSWORDHASH, PasswordHasher.hash(tempPassword))
            put(DbContract.UROLE, role)
            put(DbContract.USTATUS, status)
            put(DbContract.UACCEPTEDTERMS, 1)
            put(DbContract.UFORCEPWCHANGE, 1)
            put(DbContract.UCREATEDAT, System.currentTimeMillis())
            put(DbContract.UISPREMIUMUSER, 0)
        }
        return db.writableDatabase.insertOrThrow(DbContract.TUSERS, null, cv)
    }

    private fun setUserPremium(userId: Long, enabled: Boolean): Int {
        val cv = android.content.ContentValues().apply { put(DbContract.UISPREMIUMUSER, if (enabled) 1 else 0) }
        return db.writableDatabase.update(DbContract.TUSERS, cv, "${DbContract.UID}=?", arrayOf(userId.toString()))
    }

    private fun adminUpdateUser(id: Long, name: String, role: String, status: String): Int {
        val cv = android.content.ContentValues().apply {
            put(DbContract.UNAME, name.trim())
            put(DbContract.UROLE, role)
            put(DbContract.USTATUS, status)
        }
        return db.writableDatabase.update(DbContract.TUSERS, cv, "${DbContract.UID}=?", arrayOf(id.toString()))
    }

    private fun adminResetPassword(userId: Long, tempPassword: CharArray): Int {
        val cv = android.content.ContentValues().apply {
            put(DbContract.UPASSWORDHASH, PasswordHasher.hash(tempPassword))
            put(DbContract.UFORCEPWCHANGE, 1)
        }
        return db.writableDatabase.update(DbContract.TUSERS, cv, "${DbContract.UID}=?", arrayOf(userId.toString()))
    }

    private fun adminGetUserDependencies(userId: Long): AdminUserDependencyRow {
        fun count(table: String, col: String): Int {
            db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE $col=?", arrayOf(userId.toString())).use {
                return if (it.moveToFirst()) it.getInt(0) else 0
            }
        }
        return AdminUserDependencyRow(
            count(DbContract.TDECKS, DbContract.DOWNERUSERID),
            count(DbContract.TSTUDYSESSIONS, DbContract.SUSERID),
            count(DbContract.TREPORTS, DbContract.RREPORTERUSERID),
            count(DbContract.TCARDPROGRESS, DbContract.PUSERID)
        )
    }

    private fun adminDeleteUserIfSafe(currentAdminUserId: Long, userId: Long): Int {
        if (userId <= 0L || userId == currentAdminUserId) return 0
        val role = db.readableDatabase
            .rawQuery("SELECT ${DbContract.UROLE} FROM ${DbContract.TUSERS} WHERE ${DbContract.UID}=? LIMIT 1", arrayOf(userId.toString()))
            .use { c -> if (!c.moveToFirst()) return 0; c.getString(0) }
        if (role == DbContract.ROLE_ADMIN) return 0
        val deps = adminGetUserDependencies(userId)
        if (deps.deckCount > 0 || deps.studyCount > 0 || deps.reportCount > 0 || deps.progressCount > 0) return 0
        return db.writableDatabase.delete(DbContract.TUSERS, "${DbContract.UID}=?", arrayOf(userId.toString()))
    }

    // ─── Adapter ───────────────────────────────────────────────────────────────

    private inner class UsersAdapter(
        private val premiumIds: () -> Set<Long>,
        private val avatarColors: List<Int>,
        private val onEdit: (SimpleUserRow) -> Unit,
        private val onResetPw: (SimpleUserRow) -> Unit,
        private val onDelete: (SimpleUserRow) -> Unit
    ) : RecyclerView.Adapter<UsersAdapter.VH>() {

        private val items = mutableListOf<SimpleUserRow>()

        fun submit(newItems: List<SimpleUserRow>) {
            items.clear(); items.addAll(newItems); notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemAdminUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(private val b: ItemAdminUserBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(u: SimpleUserRow) {
                // ── Avatar initials ──────────────────────────────────────
                val initials = u.name.trim()
                    .split(" ").filter { it.isNotEmpty() }
                    .take(2).joinToString("") { it[0].uppercaseChar().toString() }
                    .ifEmpty { "?" }
                b.tvAvatarInitials.text = initials
                // Pick a color deterministically based on user id
                val color = avatarColors[(u.id % avatarColors.size).toInt().coerceAtLeast(0)]
                b.tvAvatarInitials.background.mutate().setTint(color)

                // ── Name & email ─────────────────────────────────────────
                b.tvName.text = u.name
                b.tvEmail.text = u.email

                // ── Role badge ───────────────────────────────────────────
                b.tvBadgeRole.text = u.role

                // ── Status badge ─────────────────────────────────────────
                b.tvBadgeStatus.text = u.status

                // ── Premium badge ────────────────────────────────────────
                b.tvBadgePremium.visibility =
                    if (premiumIds().contains(u.id)) View.VISIBLE else View.GONE

                // ── Actions ──────────────────────────────────────────────
                b.btnEdit.setOnClickListener { onEdit(u) }
                b.btnResetPw.setOnClickListener { onResetPw(u) }
                b.btnDelete.setOnClickListener { onDelete(u) }
            }
        }
    }
}
