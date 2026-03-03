package com.example.stardeckapplication.ui.auth

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityRegisterBinding
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.google.android.material.snackbar.Snackbar

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private val db by lazy { StarDeckDbHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnCreateAccount.setOnClickListener { attemptRegister() }
        b.btnGoLogin.setOnClickListener {
            finish()
        }
    }

    private fun attemptRegister() {
        b.tilName.error = null
        b.tilEmail.error = null
        b.tilPassword.error = null
        b.tilConfirm.error = null

        val name = b.etName.text?.toString().orEmpty()
        val email = b.etEmail.text?.toString().orEmpty()
        val pw = b.etPassword.text?.toString().orEmpty()
        val confirm = b.etConfirm.text?.toString().orEmpty()
        val accepted = b.cbTerms.isChecked

        var ok = true
        if (name.trim().length < 2) {
            b.tilName.error = "Enter your name"
            ok = false
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.error = "Enter a valid email"
            ok = false
        }
        if (pw.length < 8) {
            b.tilPassword.error = "Password must be at least 8 characters"
            ok = false
        }
        if (confirm != pw) {
            b.tilConfirm.error = "Passwords do not match"
            ok = false
        }
        if (!accepted) {
            Snackbar.make(b.root, "You must accept Terms & Conditions", Snackbar.LENGTH_SHORT).show()
            ok = false
        }
        if (!ok) return

        try {
            db.registerUser(name, email, pw.toCharArray(), acceptedTerms = accepted)
            Snackbar.make(b.root, "Account created. Please login.", Snackbar.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } catch (e: SQLiteConstraintException) {
            Snackbar.make(b.root, "Email already exists. Try logging in.", Snackbar.LENGTH_LONG).show()
        }
    }
}