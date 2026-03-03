package com.example.stardeckapplication.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityChangePasswordBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.model.UserSession
import com.example.stardeckapplication.ui.home.AdminHomeActivity
import com.example.stardeckapplication.ui.home.ManagerHomeActivity
import com.example.stardeckapplication.ui.home.UserHomeActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar

class ChangePasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_FORCE_MODE = "extra_force_mode"
    }

    private lateinit var b: ActivityChangePasswordBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val sessionMgr by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(b.root)

        val user = intent.getParcelableExtra<UserSession>(EXTRA_SESSION)
        val forceMode = intent.getBooleanExtra(EXTRA_FORCE_MODE, false)

        if (user == null) {
            finish()
            return
        }

        // If force mode, we don’t require old password field
        b.tilOldPassword.isEnabled = !forceMode
        b.tilOldPassword.visibility = if (forceMode) android.view.View.GONE else android.view.View.VISIBLE

        b.btnSave.setOnClickListener {
            save(user, forceMode)
        }
    }

    private fun save(user: UserSession, forceMode: Boolean) {
        b.tilOldPassword.error = null
        b.tilNewPassword.error = null
        b.tilConfirm.error = null

        val oldPw = b.etOldPassword.text?.toString().orEmpty()
        val newPw = b.etNewPassword.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()

        var ok = true
        if (!forceMode && oldPw.length < 8) {
            b.tilOldPassword.error = "Enter your current password"
            ok = false
        }
        if (newPw.length < 8) {
            b.tilNewPassword.error = "New password must be at least 8 characters"
            ok = false
        }
        if (confirm != newPw) {
            b.tilConfirm.error = "Passwords do not match"
            ok = false
        }
        if (!ok) return

        // If not forced, verify old password by authenticating again
        if (!forceMode) {
            val reAuth = db.authenticate(user.email, oldPw.toCharArray())
            if (reAuth == null) {
                Snackbar.make(b.root, "Current password is wrong", Snackbar.LENGTH_SHORT).show()
                return
            }
        }

        db.updatePassword(user.id, newPw.toCharArray(), forcePwChange = false)

        val updated = user.copy(forcePasswordChange = false)
        sessionMgr.save(updated)

        val next = when (updated.role) {
            DbContract.ROLE_ADMIN -> AdminHomeActivity::class.java
            DbContract.ROLE_MANAGER -> ManagerHomeActivity::class.java
            else -> UserHomeActivity::class.java
        }
        startActivity(android.content.Intent(this, next))
        finish()
    }
}