package com.example.stardeckapplication.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserSession(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val forcePasswordChange: Boolean
) : Parcelable