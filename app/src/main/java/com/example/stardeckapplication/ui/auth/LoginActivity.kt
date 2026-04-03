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
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.db.UserDao.ResetPasswordResult
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

    private val userDao by lazy { UserDao(StarDeckDbHelper(this)) }
    private val session by lazy { SessionManager(this) }

    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        executor.execute {
            try {
                userDao.ensureDemoAccounts()
            } catch (_: Exception) {
            }
        }

        b.etEmail.addTextChangedListener { b.tilEmail.error = null }
        b.etPassword.addTextChangedListener { b.tilPassword.error = null }

        b.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        b.btnLogin.setOnClickListener { attemptLogin() }

        b.btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        b.btnForgotPassword.setOnClickListener {
            showResetPasswordDialog()
        }

        b.btnGoogle.setOnClickListener { openLink("https://accounts.google.com/") }
        b.btnFacebook.setOnClickListener { openLink("https://www.facebook.com/login/") }
        b.btnX.setOnClickListener { openLink("https://x.com/login") }

        b.btnTerms.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    com.example.stardeckapplication.ui.legal.TermsActivity::class.java
                )
            )
        }

        b.btnPrivacy.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    com.example.stardeckapplication.ui.legal.PrivacyPolicyActivity::class.java
                )
            )
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
            b.tilEmail.error = getString(R.string.error_valid_email)
            ok = false
        }

        if (pw.isBlank()) {
            b.tilPassword.error = getString(R.string.error_enter_password)
            ok = false
        }

        if (!ok) return

        hideKeyboard()
        setLoading(true)

        val pwChars = pw.toCharArray()

        executor.execute {
            try {
                val user = userDao.authenticate(email, pwChars)
                pwChars.fill('\u0000')

                if (user == null) {
                    postUi {
                        setLoading(false)
                        Snackbar.make(
                            b.root,
                            getString(R.string.msg_wrong_email_or_password),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    return@execute
                }

                if (user.status == DbContract.STATUS_DISABLED) {
                    postUi {
                        setLoading(false)
                        Snackbar.make(
                            b.root,
                            getString(R.string.msg_account_disabled_contact_admin),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@execute
                }

                val previous = userDao.getLastLoginAt(user.id)
                val now = System.currentTimeMillis()
                userDao.updateLastLoginAt(user.id, now)

                postUi {
                    setLoading(false)

                    val msg = if (previous == null) {
                        getString(R.string.msg_first_login)
                    } else {
                        getString(R.string.msg_welcome_back, fmt(previous))
                    }

                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

                    session.save(user)

                    if (user.forcePasswordChange) {
                        val intent = Intent(this, ChangePasswordActivity::class.java).apply {
                            putExtra(ChangePasswordActivity.EXTRA_SESSION, user)
                            putExtra(ChangePasswordActivity.EXTRA_FORCE_MODE, true)
                        }
                        startActivity(intent)
                        finish()
                        return@postUi
                    }

                    goHome(user.role)
                }
            } catch (_: Exception) {
                pwChars.fill('\u0000')
                postUi {
                    setLoading(false)
                    Snackbar.make(
                        b.root,
                        getString(R.string.msg_login_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
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
                tilEmail.error = getString(R.string.error_valid_email)
                ok = false
            }

            val strongMsg = strongPasswordError(p1)
            if (strongMsg != null) {
                tilNewPw.error = strongMsg
                ok = false
            }

            if (p1 != p2) {
                tilConfirmPw.error = getString(R.string.error_password_mismatch)
                ok = false
            }

            if (!ok) return@setOnClickListener

            setResetLoading(true)
            val pwChars = p1.toCharArray()

            executor.execute {
                val result: ResetPasswordResult = try {
                    userDao.resetPasswordByEmailOnly(
                        email = email,
                        newPassword = pwChars
                    )
                } catch (_: Exception) {
                    ResetPasswordResult.NOT_FOUND
                } finally {
                    pwChars.fill('\u0000')
                }

                postUi {
                    setResetLoading(false)

                    when (result) {
                        ResetPasswordResult.SUCCESS -> {
                            Snackbar.make(
                                b.root,
                                getString(R.string.msg_password_updated),
                                Snackbar.LENGTH_LONG
                            ).show()
                            b.etPassword.setText("")
                            dialog.dismiss()
                        }

                        ResetPasswordResult.DISABLED -> {
                            tilEmail.error = getString(R.string.error_account_disabled)
                        }

                        ResetPasswordResult.NOT_FOUND -> {
                            tilEmail.error = getString(R.string.error_email_not_found)
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun strongPasswordError(pw: String): String? {
        if (pw.length < 8) return getString(R.string.error_password_min_length)
        if (pw.any { it.isWhitespace() }) return getString(R.string.error_password_no_spaces)
        if (!pw.any { it.isUpperCase() }) return getString(R.string.error_password_uppercase)
        if (!pw.any { it.isLowerCase() }) return getString(R.string.error_password_lowercase)
        if (!pw.any { it.isDigit() }) return getString(R.string.error_password_number)
        if (!pw.any { !it.isLetterOrDigit() }) return getString(R.string.error_password_symbol)
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
            Snackbar.make(
                b.root,
                getString(R.string.msg_no_browser),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun postUi(block: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) block()
        }
    }

    private fun fmt(ms: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
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