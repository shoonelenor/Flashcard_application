package com.example.stardeckapplication.db

object DbContract {

    const val DB_NAME = "stardeck.db"
    const val DB_VERSION = 10

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
    const val U_IS_PREMIUM_USER = "is_premium_user"
    const val U_LAST_LOGIN_AT = "last_login_at"

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
    const val D_IS_PREMIUM = "is_premium"
    const val D_IS_PUBLIC = "is_public" // 0/1

    const val DECK_ACTIVE = "active"
    const val DECK_HIDDEN = "hidden"

    // ---------- Cards ----------
    const val T_CARDS = "cards"
    const val C_ID = "_id"
    const val C_DECK_ID = "deck_id"
    const val C_FRONT = "front"
    const val C_BACK = "back"
    const val C_CREATED_AT = "created_at"

    // ---------- Card Progress (SRS-ready) ----------
    const val T_CARD_PROGRESS = "card_progress"
    const val P_USER_ID = "user_id"
    const val P_CARD_ID = "card_id"
    const val P_DUE_AT = "due_at"
    const val P_LAST_REVIEWED_AT = "last_reviewed_at"
    const val P_INTERVAL_DAYS = "interval_days"
    const val P_EASE_FACTOR = "ease_factor"
    const val P_REVIEW_COUNT = "review_count"
    const val P_LAPSE_COUNT = "lapse_count"
    const val P_LAST_RESULT = "last_result"

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