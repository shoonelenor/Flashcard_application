package com.example.stardeckapplication.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityPremiumDemoBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class PremiumDemoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETURN_DECK_ID = "return_deck_id"
    }

    private lateinit var b: ActivityPremiumDemoBinding
    private val session by lazy { SessionManager(this) }
    private val db by lazy { StarDeckDbHelper(this) }

    private var returnDeckId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPremiumDemoBinding.inflate(layoutInflater)
        setContentView(b.root)

        returnDeckId = intent.getLongExtra(EXTRA_RETURN_DECK_ID, -1L)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Premium (Demo)"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish(); return
        }

        b.toolbar.setNavigationOnClickListener { finish() }

        b.chipMonthly.isChecked = true

        b.btnAddDemoDeck.setOnClickListener {
            val deckId = db.createPremiumDemoDeckForUser(me.id)
            if (deckId > 0) {
                Snackbar.make(b.root, "Premium demo deck added", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(b.root, "Could not add demo deck", Snackbar.LENGTH_LONG).show()
            }
        }

        b.btnPay.setOnClickListener { showPaymentDialog(me.id) }
        b.btnCancelPremium.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Premium (Demo)?")
                .setMessage("This will lock premium decks again.")
                .setPositiveButton("Cancel Premium") { _, _ ->
                    db.setUserPremium(me.id, false)
                    render(me.id)
                    Snackbar.make(b.root, "Premium disabled (demo)", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Keep", null)
                .show()
        }

        render(me.id)
    }

    private fun render(userId: Long) {
        val premium = db.isUserPremium(userId)

        b.tvStatus.text = if (premium) "⭐ Premium is active" else "🔒 Premium is locked"
        b.btnCancelPremium.isEnabled = premium
        b.btnCancelPremium.alpha = if (premium) 1f else 0.5f

        b.btnPay.text = if (premium) "Pay Again (Demo)" else "Upgrade (Demo)"
    }

    private fun showPaymentDialog(userId: Long) {
        val plan = if (b.chipYearly.isChecked) "Yearly" else "Monthly"
        val price = if (plan == "Yearly") "$19.99/year (demo)" else "$2.99/month (demo)"

        MaterialAlertDialogBuilder(this)
            .setTitle("Demo Checkout")
            .setMessage("Plan: $plan\nPrice: $price\n\nChoose the outcome (demo):")
            .setPositiveButton("Pay Success") { _, _ ->
                db.setUserPremium(userId, true)
                render(userId)

                MaterialAlertDialogBuilder(this)
                    .setTitle("Payment Successful ✅")
                    .setMessage("Premium is now active (demo). Premium decks are unlocked.")
                    .setPositiveButton("Continue") { _, _ ->
                        // If user came here from a locked deck, open it now
                        if (returnDeckId > 0) {
                            startActivity(
                                Intent(this, DeckCardsActivity::class.java)
                                    .putExtra(DeckCardsActivity.EXTRA_DECK_ID, returnDeckId)
                            )
                        }
                        finish()
                    }
                    .show()
            }
            .setNeutralButton("Pay Fail") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Payment Failed ❌")
                    .setMessage("This is a demo failure. Please try again (demo).")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}