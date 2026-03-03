package com.example.stardeckapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.ui.auth.LoginActivity
import com.example.stardeckapplication.ui.home.AdminHomeActivity
import com.example.stardeckapplication.ui.home.ManagerHomeActivity
import com.example.stardeckapplication.ui.home.UserHomeActivity
import com.example.stardeckapplication.util.SessionManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SessionManager(this).load()

        // If session exists but user is disabled, force logout
        if (session != null && session.status == DbContract.STATUS_DISABLED) {
            SessionManager(this).clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (session == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val next = when (session.role) {
            DbContract.ROLE_ADMIN -> AdminHomeActivity::class.java
            DbContract.ROLE_MANAGER -> ManagerHomeActivity::class.java
            else -> UserHomeActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }
}