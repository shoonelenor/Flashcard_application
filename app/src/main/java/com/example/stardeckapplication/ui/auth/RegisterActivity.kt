package com.example.stardeckapplication.ui.auth

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityRegisterBinding
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDao
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding

    // Use UserDao (which has registerUser), backed by StarDeckDbHelper
    private val userDao  by lazy { UserDao(StarDeckDbHelper(this)) }
    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etName.addTextChangedListener     { b.tilName.error     = null }
        b.etEmail.addTextChangedListener    { b.tilEmail.error    = null }
        b.etPassword.addTextChangedListener { b.tilPassword.error = null }
        b.etConfirm.addTextChangedListener  { b.tilConfirm.error  = null }

        setupTermsClickableText()
        updateCreateButtonEnabled()

        b.cbTerms.setOnCheckedChangeListener { _, _ -> updateCreateButtonEnabled() }
        b.btnCreateAccount.setOnClickListener { attemptRegister() }
        b.btnGoLogin.setOnClickListener       { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun setupTermsClickableText() {
        val full         = "I agree to Terms and Privacy"
        val spannable    = SpannableString(full)
        val termsStart   = full.indexOf("Terms")
        val termsEnd     = termsStart + "Terms".length
        val privacyStart = full.indexOf("Privacy")
        val privacyEnd   = privacyStart + "Privacy".length
        val linkColor    = ContextCompat.getColor(this, R.color.stardeck_link)

        val termsSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(
                    Intent(
                        this@RegisterActivity,
                        com.example.stardeckapplication.ui.legal.TermsActivity::class.java
                    )
                )
            }
        }
        val privacySpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(
                    Intent(
                        this@RegisterActivity,
                        com.example.stardeckapplication.ui.legal.PrivacyPolicyActivity::class.java
                    )
                )
            }
        }

        spannable.setSpan(
            termsSpan,
            termsStart,
            termsEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            privacySpan,
            privacyStart,
            privacyEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        b.cbTerms.text           = spannable
        b.cbTerms.movementMethod = LinkMovementMethod.getInstance()
        b.cbTerms.highlightColor = android.graphics.Color.TRANSPARENT
        b.cbTerms.setLinkTextColor(linkColor)
    }

    private fun updateCreateButtonEnabled() {
        b.btnCreateAccount.isEnabled = b.cbTerms.isChecked && !inFlight.get()
    }

    private fun attemptRegister() {
        if (inFlight.get()) return

        b.tilName.error     = null
        b.tilEmail.error    = null
        b.tilPassword.error = null
        b.tilConfirm.error  = null

        val name     = b.etName.text?.toString().orEmpty().trim()
        val email    = b.etEmail.text?.toString().orEmpty().trim().lowercase()
        val pw       = b.etPassword.text?.toString().orEmpty()
        val confirm  = b.etConfirm.text?.toString().orEmpty()
        val accepted = b.cbTerms.isChecked

        var ok = true
        if (name.length < 2) {
            b.tilName.error = "Enter your name"
            ok = false
        }
        if (email.isBlank() ||
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            b.tilEmail.error = "Enter a valid email"
            ok = false
        }
        val pwErr = strongPasswordError(pw)
        if (pwErr != null) {
            b.tilPassword.error = pwErr
            ok = false
        }
        if (confirm != pw) {
            b.tilConfirm.error = "Passwords do not match"
            ok = false
        }
        if (!accepted) {
            Snackbar.make(
                b.root,
                "Please accept Terms and Privacy",
                Snackbar.LENGTH_SHORT
            ).show()
            ok = false
        }
        if (!ok) return

        hideKeyboard()
        setLoading(true)

        val pwChars = pw.toCharArray()
        executor.execute {
            try {
                // call into UserDao, not StarDeckDbHelper
                userDao.registerUser(name, email, pwChars, acceptedTerms = accepted)
                pwChars.fill('\u0000')
                runOnUiThread {
                    setLoading(false)
                    Snackbar.make(
                        b.root,
                        "Account created. Please log in.",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            } catch (_: SQLiteConstraintException) {
                pwChars.fill('\u0000')
                runOnUiThread {
                    setLoading(false)
                    b.tilEmail.error = "Email already exists"
                }
            } catch (_: Exception) {
                pwChars.fill('\u0000')
                runOnUiThread {
                    setLoading(false)
                    Snackbar.make(
                        b.root,
                        "Registration failed. Please try again.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun strongPasswordError(pw: String): String? {
        if (pw.length < 8)                     return "At least 8 characters"
        if (pw.any { it.isWhitespace() })      return "No spaces allowed"
        if (!pw.any { it.isUpperCase() })      return "Add 1 uppercase letter (A-Z)"
        if (!pw.any { it.isLowerCase() })      return "Add 1 lowercase letter (a-z)"
        if (!pw.any { it.isDigit() })          return "Add 1 number (0-9)"
        if (!pw.any { !it.isLetterOrDigit() }) return "Add 1 symbol (!@#)"
        return null
    }

    private fun setLoading(loading: Boolean) {
        inFlight.set(loading)
        b.progressRegister.visibility = if (loading) View.VISIBLE else View.GONE
        b.btnGoLogin.isEnabled        = !loading
        b.etName.isEnabled            = !loading
        b.etEmail.isEnabled           = !loading
        b.etPassword.isEnabled        = !loading
        b.etConfirm.isEnabled         = !loading
        b.cbTerms.isEnabled           = !loading
        updateCreateButtonEnabled()
    }

    private fun hideKeyboard() {
        val v = currentFocus ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(v.windowToken, 0)
    }
}