package com.example.stardeckapplication.ui.admin

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.ReportDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import java.text.SimpleDateFormat
import java.util.*

class AdminTicketsActivity : AppCompatActivity() {

    private lateinit var dao: ReportDao
    private lateinit var listView: ListView
    private lateinit var emptyLayout: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvOpenCount: TextView
    private lateinit var progressBar: ProgressBar

    private var allTickets = listOf<ReportDao.TicketRow>()
    private var filtered   = listOf<ReportDao.TicketRow>()
    private var showFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_tickets)

        supportActionBar?.apply {
            title = "Support Tickets"
            setDisplayHomeAsUpEnabled(true)
        }

        dao         = ReportDao(StarDeckDbHelper(this))
        listView    = findViewById(R.id.listViewTickets)
        emptyLayout = findViewById(R.id.layoutTicketsEmpty)
        tvOpenCount = findViewById(R.id.tvTicketOpenCount)
        progressBar = findViewById(R.id.progressTickets)

        // get the "No tickets yet" TextView that lives inside the empty state layout
        tvEmpty = emptyLayout.findViewById(R.id.tvTicketsEmptyLabel)

        val btnAll      = findViewById<Button>(R.id.btnFilterAll)
        val btnOpen     = findViewById<Button>(R.id.btnFilterOpen)
        val btnResolved = findViewById<Button>(R.id.btnFilterResolved)

        btnAll.setOnClickListener      { applyFilter("all");      updateFilterButtons(btnAll, btnOpen, btnResolved) }
        btnOpen.setOnClickListener     { applyFilter("open");     updateFilterButtons(btnOpen, btnAll, btnResolved) }
        btnResolved.setOnClickListener { applyFilter("resolved"); updateFilterButtons(btnResolved, btnAll, btnOpen) }

        loadTickets()
    }

    private fun loadTickets() {
        progressBar.visibility = View.VISIBLE
        allTickets = dao.adminGetAllTickets()
        val openCount = dao.getTicketCount(DbContract.REPORT_OPEN)
        tvOpenCount.text = "$openCount open ticket${if (openCount != 1) "s" else ""}"
        progressBar.visibility = View.GONE
        applyFilter(showFilter)
    }

    private fun applyFilter(filter: String) {
        showFilter = filter
        filtered = when (filter) {
            "open"     -> allTickets.filter { it.status == DbContract.REPORT_OPEN }
            "resolved" -> allTickets.filter { it.status == DbContract.REPORT_RESOLVED }
            else       -> allTickets
        }
        if (filtered.isEmpty()) {
            listView.visibility    = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            listView.visibility    = View.VISIBLE
            emptyLayout.visibility = View.GONE
            listView.adapter = TicketAdapter(filtered)
        }
    }

    private fun updateFilterButtons(active: Button, vararg inactive: Button) {
        active.alpha     = 1.0f
        active.isEnabled = false
        inactive.forEach { it.alpha = 0.5f; it.isEnabled = true }
    }

    // ── Adapter ───────────────────────────────────────────────────────────

    inner class TicketAdapter(private val items: List<ReportDao.TicketRow>) :
        ArrayAdapter<ReportDao.TicketRow>(this@AdminTicketsActivity, 0, items) {

        private val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: layoutInflater.inflate(R.layout.item_ticket, parent, false)

            val ticket = items[position]

            view.findViewById<TextView>(R.id.tvTicketId).text       = "#${ticket.reportId}"
            view.findViewById<TextView>(R.id.tvTicketReason).text   = ticket.reasonLabel
            view.findViewById<TextView>(R.id.tvTicketReporter).text = "${ticket.reporterName} (${ticket.reporterEmail})"
            view.findViewById<TextView>(R.id.tvTicketDetails).text  = ticket.details ?: "No additional details"
            view.findViewById<TextView>(R.id.tvTicketDate).text     = fmt.format(Date(ticket.createdAt))

            val tvStatus = view.findViewById<TextView>(R.id.tvTicketStatus)
            val isOpen   = ticket.status == DbContract.REPORT_OPEN
            tvStatus.text = if (isOpen) "OPEN" else "RESOLVED"
            tvStatus.setBackgroundResource(
                if (isOpen) R.drawable.badge_open else R.drawable.badge_resolved
            )

            val btnAction = view.findViewById<Button>(R.id.btnTicketAction)
            btnAction.text = if (isOpen) "Resolve" else "Reopen"
            btnAction.setOnClickListener {
                val msg = if (isOpen)
                    "Mark ticket #${ticket.reportId} as resolved?"
                else
                    "Reopen ticket #${ticket.reportId}?"

                AlertDialog.Builder(this@AdminTicketsActivity)
                    .setTitle(if (isOpen) "Resolve Ticket" else "Reopen Ticket")
                    .setMessage(msg)
                    .setPositiveButton("Yes") { _, _ ->
                        if (isOpen) dao.resolveTicket(ticket.reportId)
                        else        dao.reopenTicket(ticket.reportId)
                        loadTickets()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            return view
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
