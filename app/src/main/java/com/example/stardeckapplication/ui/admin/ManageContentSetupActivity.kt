package com.example.stardeckapplication.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.util.SessionManager

class ManageContentSetupActivity : AppCompatActivity() {

    private val session by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            finish()
            return
        }

        startActivity(Intent(this, ManagePremiumContentActivity::class.java))
        finish()
    }
}
