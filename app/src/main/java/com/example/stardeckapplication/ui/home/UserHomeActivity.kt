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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { openFragment(UserHomeFragment()); true }
                R.id.nav_decks -> { openFragment(UserDecksFragment()); true }
                R.id.nav_profile -> { openFragment(UserProfileFragment()); true }
                else -> false
            }
        }

        // Load default tab safely
        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.nav_home
            openFragment(UserHomeFragment())
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}