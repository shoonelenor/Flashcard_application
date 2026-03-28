package com.example.stardeckapplication.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityUserHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class UserHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityUserHomeBinding
    private val session by lazy { SessionManager(this) }

    private var activeTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        activeTag = savedInstanceState?.getString(KEY_ACTIVE_TAG)

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showTab(TAG_HOME) { UserHomeFragment() }
                    true
                }

                R.id.nav_library -> {
                    showTab(TAG_LIBRARY) { UserDecksFragment() }
                    true
                }

                R.id.nav_study -> {
                    showTab(TAG_STUDY) { UserStudyFragment() }
                    true
                }

                R.id.nav_explore -> {
                    showTab(TAG_EXPLORE) { UserExploreFragment() }
                    true
                }


                R.id.nav_profile -> {
                    showTab(TAG_PROFILE) { UserProfileFragment() }
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.nav_home
        } else {
            b.bottomNav.selectedItemId = when (activeTag) {
                TAG_LIBRARY -> R.id.nav_library
                TAG_STUDY -> R.id.nav_study
                TAG_EXPLORE -> R.id.nav_explore
                TAG_PROFILE -> R.id.nav_profile
                else -> R.id.nav_home
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
        private const val KEY_ACTIVE_TAG = "active_tag"

        private const val TAG_HOME = "tab_home"
        private const val TAG_LIBRARY = "tab_library"
        private const val TAG_STUDY = "tab_study"
        private const val TAG_EXPLORE = "tab_explore"
        private const val TAG_PROFILE = "tab_profile"
    }
}