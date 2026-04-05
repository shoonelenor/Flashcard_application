package com.example.stardeckapplication.ui.profile

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.ReportDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager

class ReportIssueActivity : AppCompatActivity() {

    private lateinit var dao: ReportDao
    private lateinit var session: SessionManager

    private lateinit var spinner: Spinner
    private lateinit var etDetails: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvSuccess: TextView
    private lateinit var progressBar: ProgressBar

    private var reasons = listOf<ReportDao.ReportReasonItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_issue)

        supportActionBar?.apply {
            title = "Help / Report Issue"
            setDisplayHomeAsUpEnabled(true)
        }

        dao     = ReportDao(StarDeckDbHelper(this))
        session = SessionManager(this)

        spinner     = findViewById(R.id.spinnerReasonReport)
        etDetails   = findViewById(R.id.etReportDetails)
        btnSubmit   = findViewById(R.id.btnSubmitReport)
        tvSuccess   = findViewById(R.id.tvReportSuccess)
        progressBar = findViewById(R.id.progressReport)

        loadReasons()

        btnSubmit.setOnClickListener { submitReport() }
    }

    private fun loadReasons() {
        reasons = dao.getActiveReportReasons()
        if (reasons.isEmpty()) {
            Toast.makeText(this, "No issue categories available. Please try again later.", Toast.LENGTH_LONG).show()
            btnSubmit.isEnabled = false
            return
        }
        val labels = reasons.map { it.name }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun submitReport() {
        val user = session.load()
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = spinner.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= reasons.size) {
            Toast.makeText(this, "Please select an issue category.", Toast.LENGTH_SHORT).show()
            return
        }

        val details = etDetails.text.toString().trim()
        if (details.isEmpty()) {
            etDetails.error = "Please describe the issue"
            etDetails.requestFocus()
            return
        }
        if (details.length < 10) {
            etDetails.error = "Description is too short (minimum 10 characters)"
            etDetails.requestFocus()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false

        val reasonId = reasons[selectedIndex].id
        val rowId = dao.submitReport(
            reporterUserId = user.id,
            reasonId       = reasonId,
            details        = details
        )

        progressBar.visibility = View.GONE

        if (rowId > 0) {
            tvSuccess.visibility = View.VISIBLE
            etDetails.text.clear()
            spinner.setSelection(0)
            Toast.makeText(this, "Report submitted successfully!", Toast.LENGTH_LONG).show()
        } else {
            btnSubmit.isEnabled = true
            Toast.makeText(this, "Failed to submit report. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
