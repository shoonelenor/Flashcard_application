package com.example.stardeckapplication.db

object DbContract {
    const val DB_NAME = "stardeck.db"
    const val DB_VERSION = 8 // + last_login_at

    // ---------- Users ----------
    const val T_USERS = "users"
    const val U_ID = "_id"
    const val U_NAME = "name"
    const val U_EMAIL = "email"
    const val U_PASSWORD_HASH = "password_hash"
    const val U_ROLE = "role"
    const val U_STATUS = "status"
    const val U_ACCEPTED_TERMS = "accepted_terms"
    const val U_FORCE_PW_CHANGE = "force_pw_change"
    const val U_CREATED_AT = "created_at"

    // Premium demo
    const val U_IS_PREMIUM_USER = "is_premium_user" // 0/1

    // ✅ NEW: last login time
    const val U_LAST_LOGIN_AT = "last_login_at" // nullable epoch millis

    const val ROLE_ADMIN = "admin"
    const val ROLE_MANAGER = "manager"
    const val ROLE_USER = "user"

    const val STATUS_ACTIVE = "active"
    const val STATUS_DISABLED = "disabled"

    // ---------- Decks ----------
    const val T_DECKS = "decks"
    const val D_ID = "_id"
    const val D_OWNER_USER_ID = "owner_user_id"
    const val D_TITLE = "title"
    const val D_DESCRIPTION = "description"
    const val D_CREATED_AT = "created_at"
    const val D_STATUS = "status"
    const val DECK_ACTIVE = "active"
    const val DECK_HIDDEN = "hidden"

    const val D_IS_PREMIUM = "is_premium" // 0/1

    // ---------- Cards ----------
    const val T_CARDS = "cards"
    const val C_ID = "_id"
    const val C_DECK_ID = "deck_id"
    const val C_FRONT = "front"
    const val C_BACK = "back"
    const val C_CREATED_AT = "created_at"

    // ---------- Study Sessions ----------
    const val T_STUDY_SESSIONS = "study_sessions"
    const val S_ID = "_id"
    const val S_USER_ID = "user_id"
    const val S_DECK_ID = "deck_id"
    const val S_RESULT = "result"
    const val S_CREATED_AT = "created_at"

    const val RESULT_KNOWN = "known"
    const val RESULT_HARD = "hard"

    // ---------- Reports ----------
    const val T_REPORTS = "reports"
    const val R_ID = "_id"
    const val R_REPORTER_USER_ID = "reporter_user_id"
    const val R_DECK_ID = "deck_id"
    const val R_REASON = "reason"
    const val R_DETAILS = "details"
    const val R_STATUS = "status"
    const val R_CREATED_AT = "created_at"

    const val REPORT_OPEN = "open"
    const val REPORT_RESOLVED = "resolved"
}