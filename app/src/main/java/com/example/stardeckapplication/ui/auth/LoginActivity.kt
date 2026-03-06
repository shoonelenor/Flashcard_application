package com.example.stardeckapplication.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityLoginBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.home.AdminHomeActivity
import com.example.stardeckapplication.ui.home.ManagerHomeActivity
import com.example.stardeckapplication.ui.home.UserHomeActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val db by lazy { StarDeckDbHelper(this) }
    private val session by lazy { SessionManager(this) }

    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Ensure demo accounts exist (safe anytime)
        executor.execute { try { db.ensureDemoAccounts() } catch (_: Exception) {} }

        // Clear errors while typing
        b.etEmail.addTextChangedListener { b.tilEmail.error = null }
        b.etPassword.addTextChangedListener { b.tilPassword.error = null }

        // Keyboard Done -> login
        b.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }

        b.btnLogin.setOnClickListener { attemptLogin() }
        b.btnGoRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.btnForgotPassword.setOnClickListener { showResetPasswordDialog() }

        // Social icons open real pages
        b.btnGoogle.setOnClickListener { openLink("https://accounts.google.com/") }
        b.btnFacebook.setOnClickListener { openLink("https://www.facebook.com/login/") }
        b.btnX.setOnClickListener { openLink("https://x.com/login") }

        // Terms / Privacy
        b.btnTerms.setOnClickListener {
            startActivity(Intent(this, com.example.stardeckapplication.ui.legal.TermsActivity::class.java))
        }
        b.btnPrivacy.setOnClickListener {
            startActivity(Intent(this, com.example.stardeckapplication.ui.legal.PrivacyPolicyActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun attemptLogin() {
        if (inFlight.get()) return

        b.tilEmail.error = null
        b.tilPassword.error = null

        val email = b.etEmail.text?.toString().orEmpty().trim()
        val pw = b.etPassword.text?.toString().orEmpty()

        var ok = true
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.tilEmail.error = "Enter a valid email"
            ok = false
        }
        // Login screen: only require non-empty password (no strength rules here)
        if (pw.isBlank()) {
            b.tilPassword.error = "Enter your password"
            ok = false
        }
        if (!ok) return

        hideKeyboard()
        setLoading(true)

        val pwChars = pw.toCharArray()

        executor.execute {
            try {
                val user = db.authenticate(email, pwChars)
                pwChars.fill('\u0000')

                if (user == null) {
                    postUi {
                        setLoading(false)
                        Snackbar.make(b.root, "Wrong email or password", Snackbar.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                if (user.status == DbContract.STATUS_DISABLED) {
                    postUi {
                        setLoading(false)
                        Snackbar.make(b.root, "This account is disabled. Contact Admin.", Snackbar.LENGTH_LONG).show()
                    }
                    return@execute
                }

                val previous = db.getLastLoginAt(user.id)
                val now = System.currentTimeMillis()
                db.updateLastLoginAt(user.id, now)

                postUi {
                    setLoading(false)

                    val msg = if (previous == null) {
                        "Welcome! First login."
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
                        return@postUi
                    }

                    goHome(user.role)
                }
            } catch (_: Exception) {
                pwChars.fill('\u0000')
                postUi {
                    setLoading(false)
                    Snackbar.make(b.root, "Login failed. Please try again.", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showResetPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_reset_password, null)

        val tilEmail = view.findViewById<TextInputLayout>(R.id.tilResetEmail)
        val tilNewPw = view.findViewById<TextInputLayout>(R.id.tilResetNewPw)
        val tilConfirmPw = view.findViewById<TextInputLayout>(R.id.tilResetConfirmPw)

        val etEmail = view.findViewById<TextInputEditText>(R.id.etResetEmail)
        val etNewPw = view.findViewById<TextInputEditText>(R.id.etResetNewPw)
        val etConfirmPw = view.findViewById<TextInputEditText>(R.id.etResetConfirmPw)

        val btnCancel = view.findViewById<View>(R.id.btnResetCancel)
        val btnReset = view.findViewById<View>(R.id.btnResetDo)
        val progress = view.findViewById<View>(R.id.progressReset)

        etEmail.setText(b.etEmail.text?.toString().orEmpty().trim())

        etEmail.addTextChangedListener { tilEmail.error = null }
        etNewPw.addTextChangedListener { tilNewPw.error = null }
        etConfirmPw.addTextChangedListener { tilConfirmPw.error = null }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }

        fun setResetLoading(loading: Boolean) {
            progress.visibility = if (loading) View.VISIBLE else View.GONE
            btnCancel.isEnabled = !loading
            btnReset.isEnabled = !loading
            etEmail.isEnabled = !loading
            etNewPw.isEnabled = !loading
            etConfirmPw.isEnabled = !loading
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnReset.setOnClickListener {
            tilEmail.error = null
            tilNewPw.error = null
            tilConfirmPw.error = null

            val email = etEmail.text?.toString().orEmpty().trim()
            val p1 = etNewPw.text?.toString().orEmpty()
            val p2 = etConfirmPw.text?.toString().orEmpty()

            var ok = true
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Enter a valid email"
                ok = false
            }

            val strongMsg = strongPasswordError(p1)
            if (strongMsg != null) {
                tilNewPw.error = strongMsg
                ok = false
            }

            if (p1 != p2) {
                tilConfirmPw.error = "Passwords do not match"
                ok = false
            }

            if (!ok) return@setOnClickListener

            setResetLoading(true)

            val pwChars = p1.toCharArray()
            executor.execute {
                val success = try {
                    db.resetPasswordByEmail(email, pwChars)
                } catch (_: Exception) {
                    pwChars.fill('\u0000')
                    false
                }

                postUi {
                    setResetLoading(false)
                    if (success) {
                        Snackbar.make(b.root, "Password updated. Please log in.", Snackbar.LENGTH_LONG).show()
                        b.etPassword.setText("")
                        dialog.dismiss()
                    } else {
                        tilEmail.error = "Email not found"
                    }
                }
            }
        }

        dialog.show()
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
        inFlight.set(loading)
        b.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE

        b.btnLogin.isEnabled = !loading
        b.btnGoRegister.isEnabled = !loading
        b.btnForgotPassword.isEnabled = !loading
        b.btnGoogle.isEnabled = !loading
        b.btnFacebook.isEnabled = !loading
        b.btnX.isEnabled = !loading

        b.etEmail.isEnabled = !loading
        b.etPassword.isEnabled = !loading
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(b.root, "No browser app found.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard() {
        val v = currentFocus ?: return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun postUi(block: () -> Unit) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            block()
        }
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