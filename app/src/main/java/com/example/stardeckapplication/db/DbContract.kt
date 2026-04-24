package com.example.stardeckapplication.db

object DbContract {

    const val DB_NAME = "stardeck.db"
    const val DBNAME = DB_NAME
    const val DB_VERSION = 19
    const val DBVERSION = DB_VERSION

    // ── Tables ──────────────────────────────────────────────────────────────
    const val T_USERS = "users"
    const val TUSERS = T_USERS

    const val T_CATEGORIES = "categories"
    const val TCATEGORIES = T_CATEGORIES

    const val T_SUBJECTS = "subjects"
    const val TSUBJECTS = T_SUBJECTS

    const val T_LANGUAGES = "languages"
    const val TLANGUAGES = T_LANGUAGES

    const val T_ACHIEVEMENTS = "achievements"
    const val TACHIEVEMENTS = T_ACHIEVEMENTS

    const val T_USER_ACHIEVEMENTS = "user_achievements"
    const val TUSERACHIEVEMENTS = T_USER_ACHIEVEMENTS

    const val T_SUBSCRIPTION_PLANS = "subscription_plans"
    const val TSUBSCRIPTIONPLANS = T_SUBSCRIPTION_PLANS

    const val T_USER_SUBSCRIPTIONS = "user_subscriptions"
    const val TUSERSUBSCRIPTIONS = T_USER_SUBSCRIPTIONS

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

    const val T_REPORT_REASONS = "report_reasons"
    const val TREPORTREASONS = T_REPORT_REASONS

    // ── Users ───────────────────────────────────────────────────────────────
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

    // ── Categories ──────────────────────────────────────────────────────────
    const val CAT_ID = "id"
    const val CATID = CAT_ID
    const val CAT_NAME = "name"
    const val CATNAME = CAT_NAME
    const val CAT_DESCRIPTION = "description"
    const val CATDESCRIPTION = CAT_DESCRIPTION
    const val CAT_IS_ACTIVE = "is_active"
    const val CATISACTIVE = CAT_IS_ACTIVE
    const val CAT_SORT_ORDER = "sort_order"
    const val CATSORTORDER = CAT_SORT_ORDER
    const val CAT_CREATED_AT = "created_at"
    const val CATCREATEDAT = CAT_CREATED_AT

    // ── Subjects ────────────────────────────────────────────────────────────
    const val SUBJ_ID = "id"
    const val SUBJID = SUBJ_ID
    const val SUBJ_CATEGORY_ID = "category_id"
    const val SUBJCATEGORYID = SUBJ_CATEGORY_ID
    const val SUBJ_NAME = "name"
    const val SUBJNAME = SUBJ_NAME
    const val SUBJ_DESCRIPTION = "description"
    const val SUBJDESCRIPTION = SUBJ_DESCRIPTION
    const val SUBJ_IS_ACTIVE = "is_active"
    const val SUBJISACTIVE = SUBJ_IS_ACTIVE
    const val SUBJ_SORT_ORDER = "sort_order"
    const val SUBJSORTORDER = SUBJ_SORT_ORDER
    const val SUBJ_CREATED_AT = "created_at"
    const val SUBJCREATEDAT = SUBJ_CREATED_AT

    // ── Languages ───────────────────────────────────────────────────────────
    const val LANG_ID = "id"
    const val LANGID = LANG_ID
    const val LANG_NAME = "name"
    const val LANGNAME = LANG_NAME
    const val LANG_DESCRIPTION = "description"
    const val LANGDESCRIPTION = LANG_DESCRIPTION
    const val LANG_IS_ACTIVE = "is_active"
    const val LANGISACTIVE = LANG_IS_ACTIVE
    const val LANG_SORT_ORDER = "sort_order"
    const val LANGSORTORDER = LANG_SORT_ORDER
    const val LANG_CREATED_AT = "created_at"
    const val LANGCREATEDAT = LANG_CREATED_AT

    // ── Achievements ────────────────────────────────────────────────────────
    const val A_ID = "id"
    const val AID = A_ID
    const val A_TITLE = "title"
    const val ATITLE = A_TITLE
    const val A_DESCRIPTION = "description"
    const val ADESCRIPTION = A_DESCRIPTION
    const val A_METRIC_KEY = "metric_key"
    const val AMETRICKEY = A_METRIC_KEY
    const val A_TARGET_VALUE = "target_value"
    const val ATARGETVALUE = A_TARGET_VALUE
    const val A_IS_ACTIVE = "is_active"
    const val AISACTIVE = A_IS_ACTIVE
    const val A_SORT_ORDER = "sort_order"
    const val ASORTORDER = A_SORT_ORDER
    const val A_CREATED_AT = "created_at"
    const val ACREATEDAT = A_CREATED_AT

    // ── User achievements ───────────────────────────────────────────────────
    const val UA_USER_ID = "user_id"
    const val UAUSERID = UA_USER_ID
    const val UA_ACHIEVEMENT_ID = "achievement_id"
    const val UAACHIEVEMENTID = UA_ACHIEVEMENT_ID
    const val UA_UNLOCKED_AT = "unlocked_at"
    const val UAUNLOCKEDAT = UA_UNLOCKED_AT

    // ── Subscription plans ──────────────────────────────────────────────────
    const val SP_ID = "id"
    const val SPID = SP_ID
    const val SP_NAME = "name"
    const val SPNAME = SP_NAME
    const val SP_BILLING_CYCLE = "billing_cycle"
    const val SPBILLINGCYCLE = SP_BILLING_CYCLE
    const val SP_PRICE_TEXT = "price_text"
    const val SPPRICETEXT = SP_PRICE_TEXT
    const val SP_DURATION_DAYS = "duration_days"
    const val SPDURATIONDAYS = SP_DURATION_DAYS
    const val SP_DESCRIPTION = "description"
    const val SPDESCRIPTION = SP_DESCRIPTION
    const val SP_IS_ACTIVE = "is_active"
    const val SPISACTIVE = SP_IS_ACTIVE
    const val SP_SORT_ORDER = "sort_order"
    const val SPSORTORDER = SP_SORT_ORDER
    const val SP_CREATED_AT = "created_at"
    const val SPCREATEDAT = SP_CREATED_AT

    // ── User subscriptions ──────────────────────────────────────────────────
    const val US_ID = "id"
    const val USID = US_ID
    const val US_USER_ID = "user_id"
    const val USUSERID = US_USER_ID
    const val US_PLAN_ID = "plan_id"
    const val USPLANID = US_PLAN_ID
    const val US_PURCHASED_AT = "purchased_at"
    const val USPURCHASEDAT = US_PURCHASED_AT
    const val US_EXPIRES_AT = "expires_at"
    const val USEXPIRESAT = US_EXPIRES_AT
    const val US_IS_ACTIVE = "is_active"
    const val USISACTIVE = US_IS_ACTIVE

    // ── Decks ───────────────────────────────────────────────────────────────
    const val D_ID = "id"
    const val DID = D_ID
    const val D_OWNER_USER_ID = "owner_user_id"
    const val DOWNERUSERID = D_OWNER_USER_ID
    const val D_CATEGORY_ID = "category_id"
    const val DCATEGORYID = D_CATEGORY_ID
    const val D_SUBJECT_ID = "subject_id"
    const val DSUBJECTID = D_SUBJECT_ID
    const val D_LANGUAGE_ID = "language_id"
    const val DLANGUAGEID = D_LANGUAGE_ID
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

    // ── Cards ───────────────────────────────────────────────────────────────
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

    // ── Card progress ───────────────────────────────────────────────────────
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

    // ── Study sessions ──────────────────────────────────────────────────────
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

    // ── Reports ─────────────────────────────────────────────────────────────
    const val R_ID = "id"
    const val RID = R_ID
    const val R_REPORTER_USER_ID = "reporter_user_id"
    const val RREPORTERUSERID = R_REPORTER_USER_ID
    const val R_DECK_ID = "deck_id"
    const val RDECKID = R_DECK_ID
    const val R_REASON_ID = "reason_id"
    const val RREASONID = R_REASON_ID
    const val R_REASON = "reason"
    const val RREASON = R_REASON
    const val R_DETAILS = "details"
    const val RDETAILS = R_DETAILS
    const val R_STATUS = "status"
    const val RSTATUS = R_STATUS
    const val R_CREATED_AT = "created_at"
    const val RCREATEDAT = R_CREATED_AT

    // ── Report reasons ──────────────────────────────────────────────────────
    const val RR_ID = "id"
    const val RRID = RR_ID
    const val RR_TYPE = "reason_type"
    const val RRTYPE = RR_TYPE
    const val RR_TYPE_HELP = "help"
    const val RR_TYPE_CONTENT = "content"
    const val RR_NAME = "name"
    const val RRNAME = RR_NAME
    const val RR_DESCRIPTION = "description"
    const val RRDESCRIPTION = RR_DESCRIPTION
    const val RR_IS_ACTIVE = "is_active"
    const val RRISACTIVE = RR_IS_ACTIVE
    const val RR_SORT_ORDER = "sort_order"
    const val RRSORTORDER = RR_SORT_ORDER
    const val RR_CREATED_AT = "created_at"
    const val RRCREATEDAT = RR_CREATED_AT

    // ── Roles ───────────────────────────────────────────────────────────────
    const val ROLE_USER = "user"
    const val ROLEUSER = ROLE_USER
    const val ROLE_ADMIN = "admin"
    const val ROLEADMIN = ROLE_ADMIN
    const val ROLE_MANAGER = "manager"
    const val ROLEMANAGER = ROLE_MANAGER

    // ── User status ─────────────────────────────────────────────────────────
    const val STATUS_ACTIVE = "active"
    const val STATUSACTIVE = STATUS_ACTIVE
    const val STATUS_DISABLED = "disabled"
    const val STATUSDISABLED = STATUS_DISABLED

    // ── Deck status ─────────────────────────────────────────────────────────
    const val DECK_ACTIVE = "active"
    const val DECKACTIVE = DECK_ACTIVE
    const val DECK_HIDDEN = "hidden"
    const val DECKHIDDEN = DECK_HIDDEN

    // ── Report status ───────────────────────────────────────────────────────
    const val REPORT_OPEN = "open"
    const val REPORTOPEN = REPORT_OPEN
    const val REPORT_RESOLVED = "resolved"
    const val REPORTRESOLVED = REPORT_RESOLVED

    // ── Study result ────────────────────────────────────────────────────────
    const val RESULT_KNOWN = "known"
    const val RESULTKNOWN = RESULT_KNOWN
    const val RESULT_HARD = "hard"
    const val RESULTHARD = RESULT_HARD

    // ── Achievement metric keys ─────────────────────────────────────────────
    const val ACH_METRIC_DECKS_CREATED = "decks_created"
    const val ACHMETRICDECKSCREATED = ACH_METRIC_DECKS_CREATED
    const val ACH_METRIC_CARDS_CREATED = "cards_created"
    const val ACHMETRICCARDSCREATED = ACH_METRIC_CARDS_CREATED
    const val ACH_METRIC_TOTAL_STUDY = "total_study"
    const val ACHMETRICTOTALSTUDY = ACH_METRIC_TOTAL_STUDY
    const val ACH_METRIC_TODAY_STUDY = "today_study"
    const val ACHMETRICTODAYSTUDY = ACH_METRIC_TODAY_STUDY
    const val ACH_METRIC_STREAK_DAYS = "streak_days"
    const val ACHMETRICSTREAKDAYS = ACH_METRIC_STREAK_DAYS

    // ── Billing cycle ───────────────────────────────────────────────────────
    const val BILLING_MONTHLY = "monthly"
    const val BILLINGMONTHLY = BILLING_MONTHLY
    const val BILLING_YEARLY = "yearly"
    const val BILLINGYEARLY = BILLING_YEARLY
}