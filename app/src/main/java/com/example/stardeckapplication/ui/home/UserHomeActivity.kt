package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityUserHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class UserHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityUserHomeBinding
    private val session by lazy { SessionManager(this) }

    private var activeTag: String = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        activeTag = savedInstanceState?.getString(KEY_ACTIVE_TAG) ?: TAG_HOME

        b.bottomNav.setOnItemSelectedListener { item ->
            if (supportFragmentManager.isStateSaved) return@setOnItemSelectedListener false

            try {
                when (item.itemId) {
                    R.id.nav_home -> {
                        showTab(TAG_HOME) { UserHomeFragment() }
                        true
                    }
                    R.id.nav_decks -> {
                        showTab(TAG_LIBRARY) { UserDecksFragment() }
                        true
                    }
                    R.id.nav_profile -> {
                        showTab(TAG_PROFILE) { UserProfileFragment() }
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Open tab failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }

        // Load selected/default tab safely (do not open twice)
        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.nav_home
        } else {
            b.bottomNav.selectedItemId = when (activeTag) {
                TAG_LIBRARY -> R.id.nav_decks
                TAG_PROFILE -> R.id.nav_profile
                else -> R.id.nav_home
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACTIVE_TAG, activeTag)
        super.onSaveInstanceState(outState)
    }

    private fun showTab(tag: String, factory: () -> Fragment) {
        if (tag == activeTag) return

        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)

        fm.findFragmentByTag(activeTag)?.let { tx.hide(it) }

        val target = fm.findFragmentByTag(tag) ?: factory().also {
            tx.add(R.id.fragmentContainer, it, tag)
        }

        tx.show(target)

        // Commit safely
        if (!fm.isStateSaved) tx.commit() else tx.commitAllowingStateLoss()

        activeTag = tag
    }

    private companion object {
        private const val KEY_ACTIVE_TAG = "active_tag"
        private const val TAG_HOME = "tab_home"
        private const val TAG_LIBRARY = "tab_library"
        private const val TAG_PROFILE = "tab_profile"
    }
}