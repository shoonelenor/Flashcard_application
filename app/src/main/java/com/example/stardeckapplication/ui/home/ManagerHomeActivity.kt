package com.example.stardeckapplication.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityManagerHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.ui.stats.ManagerStatsFragment
import com.example.stardeckapplication.util.SessionManager

class ManagerHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityManagerHomeBinding
    private val session by lazy { SessionManager(this) }

    private var activeTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = ActivityManagerHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            finish()
            return
        }

        activeTag = savedInstanceState?.getString(KEY_ACTIVE_TAG)

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.manager_nav_home -> {
                    showTab(TAG_HOME) { ManagerDashboardFragment() }
                    true
                }

                R.id.manager_nav_decks -> {
                    showTab(TAG_DECKS) { ManagerDecksTabFragment() }
                    true
                }

                R.id.manager_nav_stats -> {
                    showTab(TAG_STATS) { ManagerStatsFragment() }
                    true
                }

                R.id.manager_nav_reports -> {
                    showTab(TAG_REPORTS) { ManagerReportsTabFragment() }
                    true
                }

                R.id.manager_nav_profile -> {
                    showTab(TAG_PROFILE) { ManagerProfileFragment() }
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.manager_nav_home
        } else {
            b.bottomNav.selectedItemId = when (activeTag) {
                TAG_DECKS   -> R.id.manager_nav_decks
                TAG_STATS   -> R.id.manager_nav_stats
                TAG_REPORTS -> R.id.manager_nav_reports
                TAG_PROFILE -> R.id.manager_nav_profile
                else        -> R.id.manager_nav_home
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
        private const val KEY_ACTIVE_TAG = "manager_active_tag"

        private const val TAG_HOME    = "manager_tab_home"
        private const val TAG_DECKS   = "manager_tab_decks"
        private const val TAG_STATS   = "manager_tab_stats"
        private const val TAG_REPORTS = "manager_tab_reports"
        private const val TAG_PROFILE = "manager_tab_profile"
    }
}
