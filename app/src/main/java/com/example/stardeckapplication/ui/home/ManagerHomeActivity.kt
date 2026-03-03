package com.example.stardeckapplication.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityManagerHomeBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class ManagerHomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityManagerHomeBinding
    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityManagerHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            finish()
            return
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.manager_nav_home -> { open(ManagerDashboardFragment()); true }
                R.id.manager_nav_decks -> { open(ManagerDecksTabFragment()); true }
                R.id.manager_nav_reports -> { open(ManagerReportsTabFragment()); true }
                R.id.manager_nav_profile -> { open(ManagerProfileFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.manager_nav_home
            open(ManagerDashboardFragment())
        }
    }

    private fun open(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}