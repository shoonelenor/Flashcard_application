package com.example.stardeckapplication.ui.legal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityTermsBinding

class TermsActivity : AppCompatActivity() {

    private lateinit var b: ActivityTermsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTermsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
    }
}