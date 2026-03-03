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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.admin_nav_home -> { open(AdminDashboardFragment()); true }
                R.id.admin_nav_accounts -> { open(AdminAccountsFragment()); true }
                R.id.admin_nav_profile -> { open(AdminProfileFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.admin_nav_home
            open(AdminDashboardFragment())
        }
    }

    private fun open(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}