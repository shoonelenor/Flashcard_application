package com.example.stardeckapplication.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityAdminHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityAdminHomeBinding
    private val session by lazy { SessionManager(this) }

    private var activeTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        activeTag = savedInstanceState?.getString(KEY_ACTIVE_TAG)

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.admin_nav_dashboard -> {
                    showTab(TAG_DASHBOARD) { AdminDashboardFragment() }
                    true
                }

                R.id.admin_nav_master_data -> {
                    showTab(TAG_MASTER_DATA) { AdminAccountsFragment() }
                    true
                }

                R.id.admin_nav_reports -> {
                    showTab(TAG_REPORTS) {
                        AdminPlaceholderFragment.newInstance(
                            title = "Reports",
                            subtitle = "This tab is ready for content reports. We will connect the real report workflow safely later.",
                            items = arrayListOf(
                                "Open content reports",
                                "Resolved reports",
                                "Review reported decks",
                                "Review reported cards or content",
                                "Moderation actions"
                            )
                        )
                    }
                    true
                }

                R.id.admin_nav_ticket -> {
                    showTab(TAG_TICKET) {
                        AdminPlaceholderFragment.newInstance(
                            title = "Trouble Ticket",
                            subtitle = "This tab is ready for support and issue tracking. We will connect real ticket logic later.",
                            items = arrayListOf(
                                "Open tickets",
                                "In progress tickets",
                                "Closed tickets",
                                "Support issue details",
                                "Ticket response workflow"
                            )
                        )
                    }
                    true
                }

                R.id.admin_nav_profile -> {
                    showTab(TAG_PROFILE) { AdminProfileFragment() }
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.admin_nav_dashboard
        } else {
            b.bottomNav.selectedItemId = when (activeTag) {
                TAG_MASTER_DATA -> R.id.admin_nav_master_data
                TAG_REPORTS -> R.id.admin_nav_reports
                TAG_TICKET -> R.id.admin_nav_ticket
                TAG_PROFILE -> R.id.admin_nav_profile
                else -> R.id.admin_nav_dashboard
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACTIVE_TAG, activeTag)
        super.onSaveInstanceState(outState)
    }

    fun openTab(itemId: Int) {
        if (b.bottomNav.selectedItemId != itemId) {
            b.bottomNav.selectedItemId = itemId
        }
    }

    private fun showTab(tag: String, factory: () -> Fragment) {
        val fm = supportFragmentManager
        if (activeTag == tag && fm.findFragmentByTag(tag)?.isVisible == true) return

        val tx = fm.beginTransaction().setReorderingAllowed(true)

        activeTag?.let { currentTag ->
            fm.findFragmentByTag(currentTag)?.let { tx.hide(it) }
        }

        val target = fm.findFragmentByTag(tag) ?: factory().also {
            tx.add(R.id.fragmentContainer, it, tag)
        }

        tx.show(target)
        tx.commit()

        activeTag = tag
    }

    private companion object {
        private const val KEY_ACTIVE_TAG = "admin_active_tag"

        private const val TAG_DASHBOARD = "admin_tab_dashboard"
        private const val TAG_MASTER_DATA = "admin_tab_master_data"
        private const val TAG_REPORTS = "admin_tab_reports"
        private const val TAG_TICKET = "admin_tab_ticket"
        private const val TAG_PROFILE = "admin_tab_profile"
    }
}