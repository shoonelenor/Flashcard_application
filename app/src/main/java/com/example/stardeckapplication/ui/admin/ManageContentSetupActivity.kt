package com.example.stardeckapplication.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class ManageContentSetupActivity : AppCompatActivity() {

    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_content_setup)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Content Setup"

        findViewById<LinearLayout>(R.id.cardCategories).setOnClickListener {
            startActivity(Intent(this, ManageCategoriesActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardLanguages).setOnClickListener {
            startActivity(Intent(this, ManageLanguagesActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardSubjects).setOnClickListener {
            startActivity(Intent(this, ManageSubjectsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardAchievements).setOnClickListener {
            startActivity(Intent(this, ManageAchievementsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardReportReasons).setOnClickListener {
            startActivity(Intent(this, ManageReportReasonsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardPremiumContent).setOnClickListener {
            startActivity(Intent(this, ManagePremiumContentActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardSubscriptionPlans).setOnClickListener {
            startActivity(Intent(this, ManageSubscriptionPlansActivity::class.java))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
