package com.example.stardeckapplication.ui.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.stardeckapplication.databinding.ActivityChangePasswordBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.model.UserSession
import com.example.stardeckapplication.ui.home.AdminHomeActivity
import com.example.stardeckapplication.ui.home.ManagerHomeActivity
import com.example.stardeckapplication.ui.home.UserHomeActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ChangePasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_FORCE_MODE = "extra_force_mode"
    }

    private lateinit var b: ActivityChangePasswordBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val sessionMgr by lazy { SessionManager(this) }

    private val executor = Executors.newSingleThreadExecutor()
    private val saving = AtomicBoolean(false)

    private var forceMode = false
    private var user: UserSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(b.root)

        user = readUserSessionExtra()
        forceMode = intent.getBooleanExtra(EXTRA_FORCE_MODE, false)

        val u = user ?: run { finish(); return }

        if (forceMode) {
            b.btnCancel.visibility = View.GONE
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Snackbar.make(b.root, "Please change your password to continue.", Snackbar.LENGTH_SHORT).show()
                }
            })
        } else {
            b.btnCancel.visibility = View.VISIBLE
            b.btnCancel.setOnClickListener { finish() }
        }

        b.etOldPassword.addTextChangedListener { b.tilOldPassword.error = null }
        b.etNewPassword.addTextChangedListener { b.tilNewPassword.error = null }
        b.etConfirm.addTextChangedListener { b.tilConfirm.error = null }

        b.btnSave.setOnClickListener { save(u) }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun save(u: UserSession) {
        if (saving.get()) return

        b.tilOldPassword.error = null
        b.tilNewPassword.error = null
        b.tilConfirm.error = null

        val oldPw = b.etOldPassword.text?.toString().orEmpty()
        val newPw = b.etNewPassword.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()

        var ok = true

        if (oldPw.isBlank()) {
            b.tilOldPassword.error = "Enter your current password"
            ok = false
        }

        val strongMsg = strongPasswordError(newPw)
        if (strongMsg != null) {
            b.tilNewPassword.error = strongMsg
            ok = false
        }

        if (newPw == oldPw && oldPw.isNotBlank()) {
            b.tilNewPassword.error = "New password must be different"
            ok = false
        }

        if (confirm != newPw) {
            b.tilConfirm.error = "Passwords do not match"
            ok = false
        }

        if (!ok) return

        setLoading(true)

        val oldChars = oldPw.toCharArray()
        val newChars = newPw.toCharArray()

        executor.execute {
            try {
                // IMPORTANT: Always validate current password (even forced mode)
                val reAuth = db.authenticate(u.email, oldChars)
                if (reAuth == null) {
                    oldChars.fill('\u0000')
                    newChars.fill('\u0000')
                    runOnUiThread {
                        setLoading(false)
                        b.tilOldPassword.error = "Current password is incorrect"
                    }
                    return@execute
                }

                db.updatePassword(u.id, newChars, forcePwChange = false)

                oldChars.fill('\u0000')
                newChars.fill('\u0000')

                val updated = u.copy(forcePasswordChange = false)
                sessionMgr.save(updated)

                runOnUiThread {
                    setLoading(false)
                    goHome(updated.role)
                }
            } catch (_: Exception) {
                oldChars.fill('\u0000')
                newChars.fill('\u0000')
                runOnUiThread {
                    setLoading(false)
                    Snackbar.make(b.root, "Failed to update password. Try again.", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun strongPasswordError(pw: String): String? {
        if (pw.length < 8) return "At least 8 characters"
        if (pw.any { it.isWhitespace() }) return "No spaces allowed"
        val hasUpper = pw.any { it.isUpperCase() }
        val hasLower = pw.any { it.isLowerCase() }
        val hasDigit = pw.any { it.isDigit() }
        val hasSymbol = pw.any { !it.isLetterOrDigit() }
        if (!hasUpper) return "Add 1 uppercase letter (A-Z)"
        if (!hasLower) return "Add 1 lowercase letter (a-z)"
        if (!hasDigit) return "Add 1 number (0-9)"
        if (!hasSymbol) return "Add 1 symbol (!@#)"
        return null
    }

    private fun setLoading(loading: Boolean) {
        saving.set(loading)
        b.progressSave.visibility = if (loading) View.VISIBLE else View.GONE

        b.btnSave.isEnabled = !loading
        b.btnCancel.isEnabled = !loading

        b.etOldPassword.isEnabled = !loading
        b.etNewPassword.isEnabled = !loading
        b.etConfirm.isEnabled = !loading
    }

    private fun goHome(role: String) {
        val next = when (role) {
            DbContract.ROLE_ADMIN -> AdminHomeActivity::class.java
            DbContract.ROLE_MANAGER -> ManagerHomeActivity::class.java
            else -> UserHomeActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }

    @Suppress("DEPRECATION")
    private fun readUserSessionExtra(): UserSession? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_SESSION, UserSession::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_SESSION)
        }
    }
}