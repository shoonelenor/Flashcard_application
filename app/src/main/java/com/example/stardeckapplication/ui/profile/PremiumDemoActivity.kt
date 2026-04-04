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

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPremiumDemoBinding.inflate(layoutInflater)
        setContentView(b.root)

        returnDeckId = intent.getLongExtra(EXTRA_RETURN_DECK_ID, -1L)
        returnReadOnlyPublic = intent.getBooleanExtra(EXTRA_RETURN_READ_ONLY_PUBLIC, false)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        setupToolbar()
        setupListeners(me.id)
        subscriptionDao.ensureDefaultsInserted()
        loadPlans()
        refreshUi(me.id)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ─────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Subscription"
        b.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners(userId: Long) {
        b.chipMonthly.setOnClickListener { renderPlanDetails() }
        b.chipYearly.setOnClickListener { renderPlanDetails() }

        b.btnPay.setOnClickListener { showConfirmDialog(userId) }

        b.btnCancelPremium.setOnClickListener { showCancelDialog(userId) }

        b.btnAddDemoDeck.setOnClickListener {
            val deckId = deckDao.createPremiumDemoDeckForUser(userId)
            val msg = if (deckId > 0) "Sample premium deck added to your library"
            else "Could not add sample deck"
            Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────

    private fun loadPlans() {
        monthlyPlan = subscriptionDao.getPlanByBillingCycle(DbContract.BILLING_MONTHLY)
        yearlyPlan  = subscriptionDao.getPlanByBillingCycle(DbContract.BILLING_YEARLY)

        b.chipMonthly.visibility = if (monthlyPlan != null) View.VISIBLE else View.GONE
        b.chipYearly.visibility  = if (yearlyPlan  != null) View.VISIBLE else View.GONE

        monthlyPlan?.let { b.chipMonthly.text = it.name }
        yearlyPlan?.let  { b.chipYearly.text  = it.name }

        // Auto-select first available plan if none checked
        if (!b.chipMonthly.isChecked && !b.chipYearly.isChecked) {
            if (monthlyPlan != null) b.chipMonthly.isChecked = true
            else if (yearlyPlan != null) b.chipYearly.isChecked = true
        }

        renderPlanDetails()
    }

    private fun selectedPlan(): SubscriptionPlanDao.PlanOption? = when {
        b.chipYearly.isChecked  && yearlyPlan  != null -> yearlyPlan
        b.chipMonthly.isChecked && monthlyPlan != null -> monthlyPlan
        monthlyPlan != null -> monthlyPlan
        yearlyPlan  != null -> yearlyPlan
        else -> null
    }

    // ─────────────────────────────────────────────────────────────
    // UI rendering
    // ─────────────────────────────────────────────────────────────

    private fun refreshUi(userId: Long) {
        val current = subscriptionDao.getCurrentPlanForUser(userId)
        val isPremium = current != null

        // Status banner
        b.tvStatus.text = if (isPremium)
            "⭐ Premium is active  •  ${current?.planName.orEmpty()}"
        else
            "🔒 Premium is not active"

        // Button states
        b.btnPay.text = if (isPremium) "Change Plan" else "Subscribe Now"
        b.btnCancelPremium.isEnabled = isPremium
        b.btnCancelPremium.alpha = if (isPremium) 1f else 0.4f

        renderPlanDetails()
    }

    private fun renderPlanDetails() {
        val plan = selectedPlan()
        if (plan == null) {
            b.tvPlanSummary.text = "No plans are currently available."
            b.btnPay.isEnabled = false
            b.btnPay.alpha = 0.5f
            return
        }

        b.btnPay.isEnabled = true
        b.btnPay.alpha = 1f
        b.tvPlanSummary.text = buildString {
            append("${plan.name}  •  ${plan.priceText}")
            append("\nBilling: ${plan.billingLabel}")
            append("\nDuration: ${plan.durationDays} day(s)")
            if (!plan.description.isNullOrBlank()) append("\n${plan.description}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Dialogs
    // ─────────────────────────────────────────────────────────────

    private fun showConfirmDialog(userId: Long) {
        val plan = selectedPlan() ?: run {
            Snackbar.make(b.root, "No plan available to subscribe.", Snackbar.LENGTH_LONG).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Subscription")
            .setMessage(
                "Plan: ${plan.name}\n" +
                        "Billing: ${plan.billingLabel}\n" +
                        "Price: ${plan.priceText}\n" +
                        "Duration: ${plan.durationDays} day(s)\n\n" +
                        "You will be charged ${plan.priceText} per ${plan.billingLabel.lowercase()}."
            )
            .setPositiveButton("Confirm") { _, _ ->
                activatePlan(userId, plan)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun activatePlan(userId: Long, plan: SubscriptionPlanDao.PlanOption) {
        val ok = subscriptionDao.activatePlanForUser(userId, plan.id)
        if (!ok) {
            Snackbar.make(b.root, "Could not activate plan. Please try again.", Snackbar.LENGTH_LONG).show()
            return
        }

        refreshUi(userId)

        MaterialAlertDialogBuilder(this)
            .setTitle("Subscription Activated")
            .setMessage("${plan.name} is now active. Premium decks are unlocked.")
            .setPositiveButton("Continue") { _, _ ->
                if (returnDeckId > 0) {
                    startActivity(
                        Intent(this, DeckCardsActivity::class.java)
                            .putExtra(DeckCardsActivity.EXTRA_DECK_ID, returnDeckId)
                            .putExtra(DeckCardsActivity.EXTRA_READ_ONLY_PUBLIC, returnReadOnlyPublic)
                    )
                }
                finish()
            }
            .show()
    }

    private fun showCancelDialog(userId: Long) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Subscription?")
            .setMessage("You will lose access to premium decks at the end of your billing period.")
            .setPositiveButton("Cancel Subscription") { _, _ ->
                subscriptionDao.cancelPremiumForUser(userId)
                loadPlans()
                refreshUi(userId)
                Snackbar.make(b.root, "Subscription cancelled.", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep Plan", null)
            .show()
    }
}