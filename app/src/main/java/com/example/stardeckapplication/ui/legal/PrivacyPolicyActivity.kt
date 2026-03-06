package com.example.stardeckapplication.ui.legal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.databinding.ActivityPrivacyPolicyBinding

class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var b: ActivityPrivacyPolicyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
    }
}