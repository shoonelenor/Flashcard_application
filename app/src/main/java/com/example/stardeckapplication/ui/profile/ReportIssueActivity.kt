package com.example.stardeckapplication.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.ReportDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.appbar.MaterialToolbar

class ReportIssueActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var dao: ReportDao

    private lateinit var spinner: Spinner
    private lateinit var etDetails: EditText
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSuccess: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_issue)

        // Wire toolbar exactly like AchievementsActivity so the back arrow works
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Help / Report Issue"

        session = SessionManager(this)
        dao     = ReportDao(StarDeckDbHelper(this))

        spinner     = findViewById(R.id.spinnerReasonReport)
        etDetails   = findViewById(R.id.etReportDetails)
        btnSubmit   = findViewById(R.id.btnSubmitReport)
        progressBar = findViewById(R.id.progressReport)
        tvSuccess   = findViewById(R.id.tvReportSuccess)

        val categories = listOf(
            "Bug / App Crash",
            "Account Issue",
            "Content Problem",
            "Performance Issue",
            "Feature Request",
            "Other"
        )
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )

        btnSubmit.setOnClickListener { submitReport() }
    }

    // ── Back navigation (same pattern as AchievementsActivity) ────────────────

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun submitReport() {
        val me = session.load()
        if (me == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val category = spinner.selectedItem?.toString().orEmpty()
        val details  = etDetails.text.toString().trim()

        if (details.length < 10) {
            etDetails.error = "Please enter at least 10 characters."
            etDetails.requestFocus()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled    = false

        val result = dao.insertReport(
            userId   = me.id.toLong(),
            category = category,
            details  = details
        )

        progressBar.visibility = View.GONE

        if (result > 0) {
            tvSuccess.visibility = View.VISIBLE
            btnSubmit.isEnabled  = false
            etDetails.isEnabled  = false
            spinner.isEnabled    = false
        } else {
            btnSubmit.isEnabled = true
            Toast.makeText(this, "Failed to submit report. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }
}
