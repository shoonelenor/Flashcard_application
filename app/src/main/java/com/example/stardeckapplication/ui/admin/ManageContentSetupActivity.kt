package com.example.stardeckapplication.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityManageContentSetupBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class ManageContentSetupActivity : AppCompatActivity() {

    private lateinit var b: ActivityManageContentSetupBinding
    private val session by lazy { SessionManager(this) }

    data class MenuItem(val label: String, val description: String, val target: Class<*>)

    private val menuItems = listOf(
        MenuItem("Categories",         "Manage deck categories",          ManageCategoriesActivity::class.java),
        MenuItem("Languages",          "Manage supported languages",       ManageLanguagesActivity::class.java),
        MenuItem("Subjects",           "Manage deck subjects",             ManageSubjectsActivity::class.java),
        MenuItem("Achievements",       "Manage user achievements",         ManageAchievementsActivity::class.java),
        MenuItem("Report Reasons",     "Manage report reason options",     ManageReportReasonsActivity::class.java),
        MenuItem("Premium Content",    "Manage premium decks & content",   ManagePremiumContentActivity::class.java),
        MenuItem("Subscription Plans", "Manage subscription tiers",        ManageSubscriptionPlansActivity::class.java)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManageContentSetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Content Setup"
        b.toolbar.setNavigationOnClickListener { finish() }

        // Hide search bar and FAB - not needed for navigation menu
        b.searchLayout.visibility = View.GONE
        b.tvItemCount.visibility  = View.GONE
        b.fabAdd.hide()

        // Show filtered or full list
        b.recyclerView.layoutManager = LinearLayoutManager(this)
        b.recyclerView.adapter = MenuAdapter(menuItems) { item ->
            startActivity(Intent(this, item.target))
        }

        b.emptyState.visibility  = View.GONE
        b.recyclerView.visibility = View.VISIBLE
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private class MenuAdapter(
        private val items: List<MenuItem>,
        private val onClick: (MenuItem) -> Unit
    ) : RecyclerView.Adapter<MenuAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_content_setup_menu, parent, false)
            return VH(view, onClick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        class VH(view: View, private val onClick: (MenuItem) -> Unit) : RecyclerView.ViewHolder(view) {
            private val tvLabel: TextView = view.findViewById(R.id.tvMenuLabel)
            private val tvDesc:  TextView = view.findViewById(R.id.tvMenuDescription)

            fun bind(item: MenuItem) {
                tvLabel.text = item.label
                tvDesc.text  = item.description
                itemView.setOnClickListener { onClick(item) }
            }
        }
    }
}
