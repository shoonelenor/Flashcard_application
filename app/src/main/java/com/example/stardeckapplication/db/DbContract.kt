package com.example.stardeckapplication.db

object DbContract {

    // ---------- DATABASE ----------

    const val DB_NAME = "stardeck.db"
    const val DBNAME = DB_NAME

    const val DB_VERSION = 11
    const val DBVERSION = DB_VERSION

    // ---------- TABLE NAMES (canonical + legacy aliases) ----------

    const val T_USERS = "users"
    const val TUSERS = T_USERS

    const val T_DECKS = "decks"
    const val TDECKS = T_DECKS

    const val T_CARDS = "cards"
    const val TCARDS = T_CARDS

    const val T_CARD_PROGRESS = "card_progress"
    const val TCARDPROGRESS = T_CARD_PROGRESS

    const val T_STUDY_SESSIONS = "study_sessions"
    const val TSTUDYSESSIONS = T_STUDY_SESSIONS

    const val T_REPORTS = "reports"
    const val TREPORTS = T_REPORTS

    // ---------- USER COLUMNS ----------

    const val U_ID = "id"
    const val UID = U_ID

    const val U_NAME = "name"
    const val UNAME = U_NAME

    const val U_EMAIL = "email"
    const val UEMAIL = U_EMAIL

    const val U_PASSWORD_HASH = "password_hash"
    const val UPASSWORDHASH = U_PASSWORD_HASH

    const val U_ROLE = "role"
    const val UROLE = U_ROLE

    const val U_STATUS = "status"
    const val USTATUS = U_STATUS

    const val U_ACCEPTED_TERMS = "accepted_terms"
    const val UACCEPTEDTERMS = U_ACCEPTED_TERMS

    const val U_FORCE_PW_CHANGE = "force_pw_change"
    const val UFORCEPWCHANGE = U_FORCE_PW_CHANGE

    const val U_CREATED_AT = "created_at"
    const val UCREATEDAT = U_CREATED_AT

    const val U_IS_PREMIUM_USER = "is_premium_user"
    const val UISPREMIUMUSER = U_IS_PREMIUM_USER

    const val U_LAST_LOGIN_AT = "last_login_at"
    const val ULASTLOGINAT = U_LAST_LOGIN_AT

    // ---------- DECK COLUMNS ----------

    const val D_ID = "id"
    const val DID = D_ID

    const val D_OWNER_USER_ID = "owner_user_id"
    const val DOWNERUSERID = D_OWNER_USER_ID

    const val D_TITLE = "title"
    const val DTITLE = D_TITLE

    const val D_DESCRIPTION = "description"
    const val DDESCRIPTION = D_DESCRIPTION

    const val D_CREATED_AT = "created_at"
    const val DCREATEDAT = D_CREATED_AT

    const val D_STATUS = "status"
    const val DSTATUS = D_STATUS

    const val D_IS_PREMIUM = "is_premium"
    const val DISPREMIUM = D_IS_PREMIUM

    const val D_IS_PUBLIC = "is_public"
    const val DISPUBLIC = D_IS_PUBLIC

    // ---------- CARD COLUMNS ----------

    const val C_ID = "id"
    const val CID = C_ID

    const val C_DECK_ID = "deck_id"
    const val CDECKID = C_DECK_ID

    const val C_FRONT = "front"
    const val CFRONT = C_FRONT

    const val C_BACK = "back"
    const val CBACK = C_BACK

    const val C_CREATED_AT = "created_at"
    const val CCREATEDAT = C_CREATED_AT

    // ---------- CARD_PROGRESS COLUMNS ----------

    const val P_USER_ID = "user_id"
    const val PUSERID = P_USER_ID

    const val P_CARD_ID = "card_id"
    const val PCARDID = P_CARD_ID

    const val P_DUE_AT = "due_at"
    const val PDUEAT = P_DUE_AT

    const val P_LAST_REVIEWED_AT = "last_reviewed_at"
    const val PLASTREVIEWEDAT = P_LAST_REVIEWED_AT

    const val P_INTERVAL_DAYS = "interval_days"
    const val PINTERVALDAYS = P_INTERVAL_DAYS

    const val P_EASE_FACTOR = "ease_factor"
    const val PEASEFACTOR = P_EASE_FACTOR

    const val P_REVIEW_COUNT = "review_count"
    const val PREVIEWCOUNT = P_REVIEW_COUNT

    const val P_LAPSE_COUNT = "lapse_count"
    const val PLAPSECOUNT = P_LAPSE_COUNT

    const val P_LAST_RESULT = "last_result"
    const val PLASTRESULT = P_LAST_RESULT

    // ---------- STUDY_SESSIONS COLUMNS ----------

    const val S_ID = "id"
    const val SID = S_ID

    const val S_USER_ID = "user_id"
    const val SUSERID = S_USER_ID

    const val S_DECK_ID = "deck_id"
    const val SDECKID = S_DECK_ID

    const val S_RESULT = "result"
    const val SRESULT = S_RESULT

    const val S_CREATED_AT = "created_at"
    const val SCREATEDAT = S_CREATED_AT

    // ---------- REPORTS COLUMNS ----------

    const val R_ID = "id"
    const val RID = R_ID

    const val R_REPORTER_USER_ID = "reporter_user_id"
    const val RREPORTERUSERID = R_REPORTER_USER_ID

    const val R_DECK_ID = "deck_id"
    const val RDECKID = R_DECK_ID

    const val R_REASON = "reason"
    const val RREASON = R_REASON

    const val R_DETAILS = "details"
    const val RDETAILS = R_DETAILS

    const val R_STATUS = "status"
    const val RSTATUS = R_STATUS

    const val R_CREATED_AT = "created_at"
    const val RCREATEDAT = R_CREATED_AT

    // ---------- ENUM / FLAG VALUES (canonical + aliases) ----------

    // User roles
    const val ROLE_USER = "user"
    const val ROLEUSER = ROLE_USER

    const val ROLE_ADMIN = "admin"
    const val ROLEADMIN = ROLE_ADMIN

    const val ROLE_MANAGER = "manager"
    const val ROLEMANAGER = ROLE_MANAGER

    // User status
    const val STATUS_ACTIVE = "active"
    const val STATUSACTIVE = STATUS_ACTIVE

    const val STATUS_DISABLED = "disabled"
    const val STATUSDISABLED = STATUS_DISABLED

    // Deck status
    const val DECK_ACTIVE = "active"
    const val DECKACTIVE = DECK_ACTIVE

    const val DECK_HIDDEN = "hidden"
    const val DECKHIDDEN = DECK_HIDDEN

    // Report status
    const val REPORT_OPEN = "open"
    const val REPORTOPEN = REPORT_OPEN

    const val REPORT_RESOLVED = "resolved"
    const val REPORTRESOLVED = REPORT_RESOLVED

    // SRS results
    const val RESULT_KNOWN = "known"
    const val RESULTKNOWN = RESULT_KNOWN

    const val RESULT_HARD = "hard"
    const val RESULTHARD = RESULT_HARD
}