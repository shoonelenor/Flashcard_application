package com.example.stardeckapplication.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityPremiumDemoBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.SubscriptionPlanDao
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class PremiumDemoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETURN_DECK_ID = "return_deck_id"
        const val EXTRA_RETURN_READ_ONLY_PUBLIC = "return_read_only_public"
    }

    private lateinit var b: ActivityPremiumDemoBinding

    private val session by lazy { SessionManager(this) }
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val subscriptionDao by lazy { SubscriptionPlanDao(dbHelper) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }

    private var returnDeckId: Long = -1L
    private var returnReadOnlyPublic: Boolean = false

    private var monthlyPlan: SubscriptionPlanDao.PlanOption? = null
    private var yearlyPlan: SubscriptionPlanDao.PlanOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPremiumDemoBinding.inflate(layoutInflater)
        setContentView(b.root)

        returnDeckId = intent.getLongExtra(EXTRA_RETURN_DECK_ID, -1L)
        returnReadOnlyPublic = intent.getBooleanExtra(EXTRA_RETURN_READ_ONLY_PUBLIC, false)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Premium (Demo)"

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        subscriptionDao.ensureDefaultsInserted()

        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnAddDemoDeck.setOnClickListener {
            val deckId = deckDao.createPremiumDemoDeckForUser(me.id)
            if (deckId > 0) {
                Snackbar.make(b.root, "Premium demo deck added", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(b.root, "Could not add demo deck", Snackbar.LENGTH_LONG).show()
            }
        }

        b.chipMonthly.setOnClickListener { renderPlanSelection() }
        b.chipYearly.setOnClickListener { renderPlanSelection() }

        b.btnPay.setOnClickListener { showPaymentDialog(me.id) }

        b.btnCancelPremium.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Premium (Demo)?")
                .setMessage("This will lock premium decks again.")
                .setPositiveButton("Cancel Premium") { _, _ ->
                    subscriptionDao.cancelPremiumForUser(me.id)
                    loadPlans()
                    render(me.id)
                    Snackbar.make(b.root, "Premium disabled (demo)", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("Keep", null)
                .show()
        }

        loadPlans()
        render(me.id)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadPlans() {
        monthlyPlan = subscriptionDao.getPlanByBillingCycle(DbContract.BILLING_MONTHLY)
        yearlyPlan = subscriptionDao.getPlanByBillingCycle(DbContract.BILLING_YEARLY)

        b.chipMonthly.visibility = if (monthlyPlan == null) View.GONE else View.VISIBLE
        b.chipYearly.visibility = if (yearlyPlan == null) View.GONE else View.VISIBLE

        monthlyPlan?.let { b.chipMonthly.text = it.name }
        yearlyPlan?.let { b.chipYearly.text = it.name }

        when {
            monthlyPlan != null && yearlyPlan == null -> b.chipMonthly.isChecked = true
            monthlyPlan == null && yearlyPlan != null -> b.chipYearly.isChecked = true
            monthlyPlan != null && yearlyPlan != null && !b.chipMonthly.isChecked && !b.chipYearly.isChecked -> {
                b.chipMonthly.isChecked = true
            }
        }

        renderPlanSelection()
    }

    private fun selectedPlan(): SubscriptionPlanDao.PlanOption? {
        return when {
            b.chipYearly.isChecked && yearlyPlan != null -> yearlyPlan
            b.chipMonthly.isChecked && monthlyPlan != null -> monthlyPlan
            monthlyPlan != null -> monthlyPlan
            yearlyPlan != null -> yearlyPlan
            else -> null
        }
    }

    private fun renderPlanSelection() {
        val plan = selectedPlan()
        if (plan == null) {
            b.tvPlanSummary.text = "No active plans are configured by admin yet."
            b.btnPay.isEnabled = false
            b.btnPay.alpha = 0.5f
            return
        }

        b.btnPay.isEnabled = true
        b.btnPay.alpha = 1f
        b.tvPlanSummary.text = buildString {
            append("${plan.name} • ${plan.priceText}")
            append("\nBilling: ${plan.billingLabel}")
            append("\nDuration: ${plan.durationDays} day(s)")
            if (!plan.description.isNullOrBlank()) {
                append("\n${plan.description}")
            }
        }
    }

    private fun render(userId: Long) {
        val current = subscriptionDao.getCurrentPlanForUser(userId)
        val premium = current != null

        b.tvStatus.text = if (premium) {
            "⭐ Premium is active • ${current?.planName.orEmpty()}"
        } else {
            "🔒 Premium is locked"
        }

        b.btnCancelPremium.isEnabled = premium
        b.btnCancelPremium.alpha = if (premium) 1f else 0.5f
        b.btnPay.text = if (premium) "Change Plan (Demo)" else "Upgrade (Demo)"
        renderPlanSelection()
    }

    private fun showPaymentDialog(userId: Long) {
        val plan = selectedPlan()
        if (plan == null) {
            Snackbar.make(b.root, "No active plan available.", Snackbar.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Demo Checkout")
            .setMessage(
                "Plan: ${plan.name}\n" +
                        "Billing: ${plan.billingLabel}\n" +
                        "Price: ${plan.priceText}\n" +
                        "Duration: ${plan.durationDays} day(s)\n\n" +
                        "Choose the outcome (demo):"
            )
            .setPositiveButton("Pay Success") { _, _ ->
                val ok = subscriptionDao.activatePlanForUser(userId, plan.id)
                if (!ok) {
                    Snackbar.make(b.root, "Could not activate plan.", Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                render(userId)

                MaterialAlertDialogBuilder(this)
                    .setTitle("Payment Successful")
                    .setMessage("${plan.name} is now active (demo). Premium decks are unlocked.")
                    .setPositiveButton("Continue") { _, _ ->
                        if (returnDeckId > 0) {
                            startActivity(
                                Intent(this, DeckCardsActivity::class.java)
                                    .putExtra(DeckCardsActivity.EXTRA_DECK_ID, returnDeckId)
                                    .putExtra(
                                        DeckCardsActivity.EXTRA_READ_ONLY_PUBLIC,
                                        returnReadOnlyPublic
                                    )
                            )
                        }
                        finish()
                    }
                    .show()
            }
            .setNeutralButton("Pay Fail") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Payment Failed")
                    .setMessage("This is a demo failure. Please try again (demo).")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}