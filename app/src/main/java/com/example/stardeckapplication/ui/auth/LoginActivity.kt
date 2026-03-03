package com.example.stardeckapplication.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityLoginBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.home.AdminHomeActivity
import com.example.stardeckapplication.ui.home.ManagerHomeActivity
import com.example.stardeckapplication.ui.home.UserHomeActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvHint.text = "" // keep hidden (layout has it, but it's gone)

        b.btnLogin.setOnClickListener { attemptLogin() }
        b.btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        b.tilEmail.error = null
        b.tilPassword.error = null

        val email = b.etEmail.text?.toString().orEmpty().trim()
        val pw = b.etPassword.text?.toString().orEmpty()

        var ok = true
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.error = "Enter a valid email"
            ok = false
        }
        if (pw.length < 8) {
            b.tilPassword.error = "Password must be at least 8 characters"
            ok = false
        }
        if (!ok) return

        val user = db.authenticate(email, pw.toCharArray())
        if (user == null) {
            Snackbar.make(b.root, "Wrong email or password", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (user.status == DbContract.STATUS_DISABLED) {
            Snackbar.make(b.root, "This account is disabled. Contact Admin.", Snackbar.LENGTH_LONG).show()
            return
        }

        // Last login: show previous then update
        val previous = db.getLastLoginAt(user.id)
        val now = System.currentTimeMillis()
        db.updateLastLoginAt(user.id, now)

        val msg = if (previous == null) {
            "Welcome! First login 😊"
        } else {
            "Welcome back! Last login: ${fmt(previous)}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

        session.save(user)

        if (user.forcePasswordChange) {
            val i = Intent(this, ChangePasswordActivity::class.java).apply {
                putExtra(ChangePasswordActivity.EXTRA_SESSION, user)
                putExtra(ChangePasswordActivity.EXTRA_FORCE_MODE, true)
            }
            startActivity(i)
            finish()
            return
        }

        goHome(user.role)
    }

    private fun fmt(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(ms))
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
}