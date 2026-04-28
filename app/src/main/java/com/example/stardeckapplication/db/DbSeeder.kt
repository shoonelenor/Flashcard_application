package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.stardeckapplication.util.PasswordHasher

/**
 * DbSeeder: all demo / initial data seeding for StarDeck.
 *
 * Seed order:
 *  1. seedReportReasons
 *  2. seedMasterData          (categories, subjects, languages)
 *  3. seedAchievements
 *  4. seedSubscriptionPlans
 *  5. seedStaffAccounts
 *  6. seedDemoDecksAndCards   (decks, cards, premium decks)
 *  7. seedFriendships
 *  8. seedStudySessions       (so Stats screens show real data)
 */
object DbSeeder {

    // ─────────────────────────────────────────────────────────────────────────
    // REPORT REASONS
    // ─────────────────────────────────────────────────────────────────────────

    fun seedReportReasons(db: SQLiteDatabase) {
        listOf(
            Triple("Bug / App Crash",            "The app crashed, froze, or something stopped working unexpectedly.",         10),
            Triple("Feature Not Working",         "A feature exists but is not behaving correctly or is broken.",               20),
            Triple("Login / Account Issue",        "Problems with signing in, password, or account access.",                    30),
            Triple("Study / Flashcard Problem",    "Issues with the study session, card flipping, or progress not saving.",     40),
            Triple("Deck or Card Not Loading",     "Decks or cards are missing, not loading, or showing blank content.",        50),
            Triple("Subscription / Payment Issue", "Problems with premium subscription, billing, or unlocking premium content.",60),
            Triple("Performance / Speed Issue",    "The app is slow, laggy, or takes too long to load.",                       70),
            Triple("UI / Display Problem",         "Something looks visually wrong, overlapping, or hard to read.",            80),
            Triple("Suggestion / Feedback",        "I have an idea or general feedback about the application.",                 90),
            Triple("Other",                        "Something else not listed above.",                                         100)
        ).forEach { (name, desc, order) ->
            ensureReportReason(db, name, desc, order)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MASTER DATA  (categories → subjects, languages)
    // ─────────────────────────────────────────────────────────────────────────

    fun seedMasterData(db: SQLiteDatabase) {
        // ── Categories ────────────────────────────────────────────────────────
        val catLanguage  = ensureCategory(db, "Language",          "Language learning decks",               10)
        val catScience   = ensureCategory(db, "Science",           "Science and nature topics",              20)
        val catTech      = ensureCategory(db, "Technology",        "Computing, software, and IT concepts",   30)
        val catMath      = ensureCategory(db, "Mathematics",       "Numbers, formulas, and logic",           40)
        val catBusiness  = ensureCategory(db, "Business",          "Business, management, and finance",      50)
        val catDesign    = ensureCategory(db, "Design",            "UI/UX and creative design concepts",     60)
        val catHistory   = ensureCategory(db, "History",           "Historical events and dates",            70)
        val catGeneral   = ensureCategory(db, "General Knowledge", "Mixed trivia and general facts",        80)

        // ── Subjects per category ─────────────────────────────────────────────
        // Language
        ensureSubject(db, catLanguage, "English",          "English vocabulary and grammar",    10)
        ensureSubject(db, catLanguage, "Japanese",         "Japanese words and phrases",        20)
        ensureSubject(db, catLanguage, "Business English", "Professional English communication",30)

        // Science
        ensureSubject(db, catScience,  "Biology",          "Life science and living organisms",  10)
        ensureSubject(db, catScience,  "Chemistry",        "Chemical elements and reactions",    20)
        ensureSubject(db, catScience,  "Human Anatomy",    "Body systems and structures",        30)

        // Technology
        ensureSubject(db, catTech,    "Programming",       "Coding concepts and languages",      10)
        ensureSubject(db, catTech,    "Databases",         "SQL and database management",         20)
        ensureSubject(db, catTech,    "Networking",        "Network protocols and security",      30)
        ensureSubject(db, catTech,    "Android Dev",       "Android and Kotlin development",      40)
        ensureSubject(db, catTech,    "Cybersecurity",     "Digital security fundamentals",       50)
        ensureSubject(db, catTech,    "Data Structures",   "Algorithms and data organisation",    60)

        // Mathematics
        ensureSubject(db, catMath,    "Algebra",           "Equations and algebraic rules",       10)
        ensureSubject(db, catMath,    "Geometry",          "Shapes, areas, and measurements",     20)

        // Business
        ensureSubject(db, catBusiness,"Project Management","PM methods and terminology",          10)
        ensureSubject(db, catBusiness,"Agile",             "Agile and Scrum practices",            20)

        // Design
        ensureSubject(db, catDesign,  "UX Design",         "User experience and usability",        10)

        // History
        ensureSubject(db, catHistory, "World History",     "Key dates and world events",           10)

        // General Knowledge
        ensureSubject(db, catGeneral, "Geography",         "Countries, capitals, and maps",        10)
        ensureSubject(db, catGeneral, "Exam Skills",       "Academic vocabulary and exam tips",    20)

        // ── Languages ─────────────────────────────────────────────────────────
        ensureLanguage(db, "English",  "English language decks",   10)
        ensureLanguage(db, "Japanese", "Japanese language decks",  20)
        ensureLanguage(db, "Burmese",  "Burmese language decks",   30)
        ensureLanguage(db, "Mixed",    "Multi-language or bilingual decks", 40)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACHIEVEMENTS
    // ─────────────────────────────────────────────────────────────────────────

    fun seedAchievements(db: SQLiteDatabase) {
        // Decks created milestones
        ensureAchievement(db, "First Deck",      "Create your very first deck",             DbContract.ACH_METRIC_DECKS_CREATED, 1,   10)
        ensureAchievement(db, "Deck Builder",    "Create 5 decks",                          DbContract.ACH_METRIC_DECKS_CREATED, 5,   20)
        ensureAchievement(db, "Deck Master",     "Create 10 decks",                         DbContract.ACH_METRIC_DECKS_CREATED, 10,  30)

        // Cards created milestones
        ensureAchievement(db, "First Card",      "Add your very first flashcard",           DbContract.ACH_METRIC_CARDS_CREATED, 1,   40)
        ensureAchievement(db, "Card Maker",      "Add 20 flashcards",                       DbContract.ACH_METRIC_CARDS_CREATED, 20,  50)
        ensureAchievement(db, "Card Expert",     "Add 50 flashcards",                       DbContract.ACH_METRIC_CARDS_CREATED, 50,  60)
        ensureAchievement(db, "Card Legend",     "Add 100 flashcards",                      DbContract.ACH_METRIC_CARDS_CREATED, 100, 70)

        // Total study sessions
        ensureAchievement(db, "First Study",     "Complete your first study session",       DbContract.ACH_METRIC_TOTAL_STUDY,   1,   80)
        ensureAchievement(db, "Study Starter",   "Complete 10 study sessions",              DbContract.ACH_METRIC_TOTAL_STUDY,   10,  90)
        ensureAchievement(db, "Study Pro",       "Complete 50 study sessions",              DbContract.ACH_METRIC_TOTAL_STUDY,   50,  100)
        ensureAchievement(db, "Study Champion",  "Complete 100 study sessions",             DbContract.ACH_METRIC_TOTAL_STUDY,   100, 110)

        // Today's study
        ensureAchievement(db, "Daily Starter",   "Study 5 cards in a single day",           DbContract.ACH_METRIC_TODAY_STUDY,   5,   120)
        ensureAchievement(db, "Daily Grinder",   "Study 20 cards in a single day",          DbContract.ACH_METRIC_TODAY_STUDY,   20,  130)

        // Streak milestones
        ensureAchievement(db, "3-Day Streak",    "Study for 3 days in a row",               DbContract.ACH_METRIC_STREAK_DAYS,   3,   140)
        ensureAchievement(db, "Week Warrior",    "Study for 7 days in a row",               DbContract.ACH_METRIC_STREAK_DAYS,   7,   150)
        ensureAchievement(db, "Month Master",    "Study for 30 days in a row",              DbContract.ACH_METRIC_STREAK_DAYS,   30,  160)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBSCRIPTION PLANS
    // ─────────────────────────────────────────────────────────────────────────

    fun seedSubscriptionPlans(db: SQLiteDatabase) {
        ensureSubscriptionPlan(
            db,
            name        = "Free",
            priceText   = "\$0.00 / forever",
            durationDays= 0,
            description = "Access all free public decks and create up to 5 personal decks.",
            sortOrder   = 10
        )
        ensureSubscriptionPlan(
            db,
            name        = "Monthly Premium",
            priceText   = "\$4.99 / month",
            durationDays= 30,
            description = "Full access to all premium decks, unlimited deck creation, and advanced stats.",
            sortOrder   = 20
        )
        ensureSubscriptionPlan(
            db,
            name        = "Yearly Premium",
            priceText   = "\$39.99 / year",
            durationDays= 365,
            description = "Best value. All premium features for a full year at a discounted price.",
            sortOrder   = 30
        )
        ensureSubscriptionPlan(
            db,
            name        = "Lifetime",
            priceText   = "\$99.99 once",
            durationDays= 36500,
            description = "One-time payment for lifetime access to all current and future premium content.",
            sortOrder   = 40
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STAFF + DEMO USER ACCOUNTS
    // ─────────────────────────────────────────────────────────────────────────

    fun seedStaffAccounts(db: SQLiteDatabase) {
        ensureUserId(db, "Admin",   "admin@stardeck.local",   "Admin@1234",   DbContract.ROLE_ADMIN,   forcePwChange = false, isPremiumUser = false)
        ensureUserId(db, "Manager", "manager@stardeck.local", "Manager@1234", DbContract.ROLE_MANAGER, forcePwChange = false, isPremiumUser = false)
        ensureUserId(db, "Shoon",   "shoon@gmail.com",        "Shoon@1234",   DbContract.ROLE_USER,    forcePwChange = false, isPremiumUser = true)
        ensureUserId(db, "Nora",    "nora@gmail.com",         "Nora@1234",    DbContract.ROLE_USER,    forcePwChange = false, isPremiumUser = false)
        ensureUserId(db, "Alex",    "alex@gmail.com",         "Alex@1234",    DbContract.ROLE_USER,    forcePwChange = false, isPremiumUser = true)
        ensureUserId(db, "Maya",    "maya@gmail.com",         "Maya@1234",    DbContract.ROLE_USER,    forcePwChange = false, isPremiumUser = false)
        ensureUserId(db, "Zin",     "zin@gmail.com",          "Zin@1234",     DbContract.ROLE_USER,    forcePwChange = false, isPremiumUser = false)
        ensureUserId(db, "Leo",     "leo@gmail.com",          "Leo@1234",     DbContract.ROLE_USER,    forcePwChange = false, isPremiumUser = true)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO DECKS + CARDS
    // ─────────────────────────────────────────────────────────────────────────

    fun seedDemoDecksAndCards(db: SQLiteDatabase) {
        val shoonId = getUserId(db, "shoon@gmail.com")
        val noraId  = getUserId(db, "nora@gmail.com")
        val alexId  = getUserId(db, "alex@gmail.com")
        val mayaId  = getUserId(db, "maya@gmail.com")
        val zinId   = getUserId(db, "zin@gmail.com")
        val leoId   = getUserId(db, "leo@gmail.com")

        // ── SHOON free decks ──────────────────────────────────────────────────
        ensureDeckWithCards(db, shoonId, "English Verbs", "Common verbs for daily communication.", isPublic = true, listOf(
            "Go"     to "To move from one place to another",
            "Eat"    to "To consume food",
            "Read"   to "To look at and understand words",
            "Write"  to "To form letters or words",
            "Speak"  to "To say words aloud",
            "Learn"  to "To gain knowledge or skill",
            "Listen" to "To pay attention to sounds",
            "Think"  to "To use your mind to reason"
        ))
        ensureDeckWithCards(db, shoonId, "Basic SQL", "Starter queries and key terms for SQL.", isPublic = true, listOf(
            "SELECT"      to "Used to retrieve data from a table",
            "WHERE"       to "Used to filter rows",
            "INSERT"      to "Used to add new rows",
            "UPDATE"      to "Used to modify existing rows",
            "DELETE"      to "Used to remove rows",
            "PRIMARY KEY" to "Unique identifier for each record",
            "FOREIGN KEY" to "Links a column to another table",
            "JOIN"        to "Combines rows from related tables"
        ))
        ensureDeckWithCards(db, shoonId, "Biology Basics", "Quick biology revision deck.", isPublic = false, listOf(
            "Cell"          to "Basic unit of life",
            "Nucleus"       to "Controls activities of the cell",
            "Mitochondria"  to "Produces energy for the cell",
            "Tissue"        to "Group of similar cells",
            "Organ"         to "Structure made of multiple tissues",
            "Photosynthesis"to "Process plants use to make food"
        ))
        ensureDeckWithCards(db, shoonId, "Agile Terms", "Common terms used in agile projects.", isPublic = true, listOf(
            "Sprint"        to "A short time-boxed period of development",
            "Backlog"       to "Ordered list of work items",
            "Scrum Master"  to "Facilitates the scrum process",
            "User Story"    to "Short description of a feature from a user view",
            "Stand-up"      to "Short daily team meeting",
            "Retrospective" to "Meeting to reflect and improve",
            "Velocity"      to "Amount of work completed in a sprint",
            "Definition of Done" to "Quality checklist for completed work"
        ))
        ensureDeckWithCards(db, shoonId, "Japanese N5", "Starter Japanese vocabulary.", isPublic = false, listOf(
            "みず (mizu)"         to "Water",
            "ひ (hi)"             to "Fire",
            "やま (yama)"         to "Mountain",
            "かわ (kawa)"         to "River",
            "がっこう (gakkou)"   to "School",
            "せんせい (sensei)"   to "Teacher",
            "ともだち (tomodachi)"to "Friend",
            "べんきょう (benkyou)"to "Study"
        ))
        ensureDeckWithCards(db, shoonId, "Android Development", "Key concepts for Android with Kotlin.", isPublic = true, listOf(
            "Activity"       to "A single screen with UI",
            "Fragment"       to "Reusable part of UI in an activity",
            "Intent"         to "Message to start an activity or service",
            "RecyclerView"   to "Displays scrollable lists efficiently",
            "ViewModel"      to "Holds UI-related data in a lifecycle-aware way",
            "LiveData"       to "Observable data holder class",
            "ViewBinding"    to "Type-safe access to layout views",
            "SQLiteDatabase" to "Android local database access class"
        ))

        // ── NORA free decks ───────────────────────────────────────────────────
        ensureDeckWithCards(db, noraId, "World Capitals", "Countries and their capitals.", isPublic = true, listOf(
            "France"    to "Paris",
            "Japan"     to "Tokyo",
            "Thailand"  to "Bangkok",
            "Australia" to "Canberra",
            "Canada"    to "Ottawa",
            "Brazil"    to "Brasilia",
            "Germany"   to "Berlin",
            "Myanmar"   to "Naypyidaw"
        ))
        ensureDeckWithCards(db, noraId, "Math Formulas", "Useful formulas for quick revision.", isPublic = false, listOf(
            "Area of rectangle"    to "length × width",
            "Area of triangle"     to "1/2 × base × height",
            "Perimeter of square"  to "4 × side",
            "Circumference"        to "2 × π × r",
            "Pythagoras"           to "a² + b² = c²",
            "Average"              to "sum ÷ count",
            "Area of circle"       to "π × r²",
            "Simple Interest"      to "(P × R × T) / 100"
        ))
        ensureDeckWithCards(db, noraId, "UI UX Terms", "Key design and usability terms.", isPublic = true, listOf(
            "Wireframe"     to "Basic layout of a screen",
            "Prototype"     to "Interactive model of a design",
            "Usability"     to "How easy a product is to use",
            "Consistency"   to "Similar elements behave the same way",
            "Accessibility" to "Design usable by more people",
            "Feedback"      to "System response to user action",
            "Affordance"    to "Hint that shows how to use an element",
            "Hierarchy"     to "Visual order to guide attention"
        ))
        ensureDeckWithCards(db, noraId, "Human Anatomy", "Simple anatomy revision deck.", isPublic = false, listOf(
            "Heart"    to "Pumps blood through the body",
            "Lungs"    to "Help with breathing",
            "Brain"    to "Controls body functions",
            "Femur"    to "Longest bone in the body",
            "Skin"     to "Largest organ of the body",
            "Rib cage" to "Protects the heart and lungs",
            "Liver"    to "Processes nutrients and detoxifies blood",
            "Kidney"   to "Filters waste from blood"
        ))
        ensureDeckWithCards(db, noraId, "Networking Basics", "Starter concepts for computer networking.", isPublic = true, listOf(
            "IP Address" to "Unique address for a device on a network",
            "Router"     to "Connects networks and forwards data",
            "Switch"     to "Connects devices in a local network",
            "LAN"        to "Local Area Network",
            "WAN"        to "Wide Area Network",
            "DNS"        to "Translates domain names to IP addresses",
            "Firewall"   to "Filters network traffic",
            "VPN"        to "Encrypted connection over a public network"
        ))

        // ── ALEX free decks ───────────────────────────────────────────────────
        ensureDeckWithCards(db, alexId, "Data Structures", "Important programming data structures.", isPublic = true, listOf(
            "Array"      to "Ordered collection of items",
            "Stack"      to "Last in, first out structure",
            "Queue"      to "First in, first out structure",
            "Tree"       to "Hierarchical node-based structure",
            "Graph"      to "Nodes connected by edges",
            "Hash Map"   to "Key-value storage with fast lookup"
        ))
        ensureDeckWithCards(db, alexId, "Chemistry Core", "Basic chemistry facts and definitions.", isPublic = true, listOf(
            "Atom"      to "Smallest unit of an element",
            "Molecule"  to "Two or more atoms bonded together",
            "pH"        to "Measure of acidity or alkalinity",
            "Catalyst"  to "Substance that speeds up a reaction",
            "Osmosis"   to "Movement of water across a membrane",
            "Enzyme"    to "Protein that speeds up reactions"
        ))
        ensureDeckWithCards(db, alexId, "Cybersecurity", "Basic security terms for digital safety.", isPublic = true, listOf(
            "Malware"          to "Software designed to harm a system",
            "Ransomware"       to "Malware that locks data for payment",
            "2FA"              to "Two-factor authentication",
            "Phishing"         to "Fake communication used to steal information",
            "Password manager" to "Tool for storing strong passwords securely",
            "TLS"              to "Protects data in transit"
        ))

        // ── MAYA free decks ───────────────────────────────────────────────────
        ensureDeckWithCards(db, mayaId, "Business English", "Professional vocabulary for workplace communication.", isPublic = true, listOf(
            "Agenda"      to "List of topics for a meeting",
            "Deadline"    to "Final time limit for a task",
            "Proposal"    to "Formal suggestion or plan",
            "Stakeholder" to "Person affected by a project or decision",
            "KPI"         to "Key Performance Indicator",
            "ROI"         to "Return on Investment"
        ))
        ensureDeckWithCards(db, mayaId, "World History Dates", "Important dates for general history review.", isPublic = true, listOf(
            "1914" to "Start of World War I",
            "1918" to "End of World War I",
            "1939" to "Start of World War II",
            "1945" to "End of World War II",
            "1969" to "First moon landing",
            "1989" to "Fall of the Berlin Wall"
        ))
        ensureDeckWithCards(db, mayaId, "Project Management", "Core PM language and ideas.", isPublic = true, listOf(
            "Scope"       to "Boundaries of project work",
            "Risk"        to "Possible event that can affect objectives",
            "Milestone"   to "Important checkpoint in a project",
            "Deliverable" to "Output produced by the project",
            "Gantt Chart" to "Visual timeline of project tasks",
            "WBS"         to "Work Breakdown Structure"
        ))

        // ── ZIN free decks ────────────────────────────────────────────────────
        ensureDeckWithCards(db, zinId, "English Idioms", "Common English idioms and meanings.", isPublic = true, listOf(
            "Break the ice"       to "Start a conversation in a relaxed way",
            "Hit the sack"        to "Go to sleep",
            "Piece of cake"       to "Very easy",
            "Under the weather"   to "Feeling unwell",
            "Bite the bullet"     to "Endure a painful situation",
            "Hit the nail"        to "Do or say something exactly right"
        ))
        ensureDeckWithCards(db, zinId, "Exam Vocabulary", "Academic words useful for exams and essays.", isPublic = true, listOf(
            "Analyze"    to "Examine something in detail",
            "Evaluate"   to "Judge value or quality",
            "Synthesize" to "Combine ideas into a whole",
            "Interpret"  to "Explain meaning",
            "Justify"    to "Give reasons to support a view",
            "Compare"    to "Show similarities and differences"
        ))

        // ── LEO free decks ────────────────────────────────────────────────────
        ensureDeckWithCards(db, leoId, "Kotlin Basics", "Key Kotlin programming concepts.", isPublic = true, listOf(
            "val"          to "Immutable (read-only) variable",
            "var"          to "Mutable variable",
            "data class"   to "Class mainly used to hold data",
            "sealed class" to "Restricted class hierarchy",
            "when"         to "Powerful conditional expression",
            "lazy"         to "Initializes value only when needed"
        ))
        ensureDeckWithCards(db, leoId, "UX Heuristics", "Common usability heuristics for better design.", isPublic = true, listOf(
            "Visibility of status"  to "Keep users informed about what is happening",
            "User control"          to "Let users undo or exit actions",
            "Consistency"           to "Use familiar patterns and behaviour",
            "Error prevention"      to "Design to reduce mistakes",
            "Recognition over recall" to "Minimise memory load",
            "Flexibility"           to "Cater to both novice and expert users"
        ))

        // ── Premium decks ─────────────────────────────────────────────────────
        premiumDecks(shoonId).forEach { b -> ensurePremiumDeck(db, shoonId, b) }
        premiumDecks(noraId).forEach  { b -> ensurePremiumDeck(db, noraId,  b) }
        premiumDecks(alexId).forEach  { b -> ensurePremiumDeck(db, alexId,  b) }
        premiumDecks(leoId).forEach   { b -> ensurePremiumDeck(db, leoId,   b) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FRIENDSHIPS
    // ─────────────────────────────────────────────────────────────────────────

    fun seedFriendships(db: SQLiteDatabase) {
        val shoonId = getUserId(db, "shoon@gmail.com")
        val noraId  = getUserId(db, "nora@gmail.com")
        val alexId  = getUserId(db, "alex@gmail.com")
        val mayaId  = getUserId(db, "maya@gmail.com")
        val zinId   = getUserId(db, "zin@gmail.com")
        val leoId   = getUserId(db, "leo@gmail.com")

        ensureFriendship(db, shoonId, noraId,  DbContract.FRIEND_ACCEPTED)
        ensureFriendship(db, shoonId, alexId,  DbContract.FRIEND_ACCEPTED)
        ensureFriendship(db, shoonId, zinId,   DbContract.FRIEND_ACCEPTED)
        ensureFriendship(db, noraId,  mayaId,  DbContract.FRIEND_ACCEPTED)
        ensureFriendship(db, noraId,  leoId,   DbContract.FRIEND_ACCEPTED)
        ensureFriendship(db, alexId,  mayaId,  DbContract.FRIEND_ACCEPTED)
        ensureFriendship(db, leoId,   zinId,   DbContract.FRIEND_PENDING)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STUDY SESSIONS  (so Stats, Leaderboard, Streaks show real data)
    // ─────────────────────────────────────────────────────────────────────────

    fun seedStudySessions(db: SQLiteDatabase) {
        val shoonId = getUserId(db, "shoon@gmail.com")
        val noraId  = getUserId(db, "nora@gmail.com")
        val alexId  = getUserId(db, "alex@gmail.com")
        val mayaId  = getUserId(db, "maya@gmail.com")
        val zinId   = getUserId(db, "zin@gmail.com")
        val leoId   = getUserId(db, "leo@gmail.com")

        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        // Shoon — 7 days consecutive streak, lots of sessions
        repeat(8) { seedSession(db, shoonId, now - it * day, DbContract.RESULT_KNOWN) }
        repeat(4) { seedSession(db, shoonId, now - it * day, DbContract.RESULT_HARD)  }

        // Nora — 5 days streak
        repeat(6) { seedSession(db, noraId, now - it * day, DbContract.RESULT_KNOWN) }
        repeat(3) { seedSession(db, noraId, now - it * day, DbContract.RESULT_HARD)  }

        // Alex — 4 days streak
        repeat(5) { seedSession(db, alexId, now - it * day, DbContract.RESULT_KNOWN) }
        repeat(2) { seedSession(db, alexId, now - it * day, DbContract.RESULT_HARD)  }

        // Maya — 3 days streak
        repeat(4) { seedSession(db, mayaId, now - it * day, DbContract.RESULT_KNOWN) }
        repeat(2) { seedSession(db, mayaId, now - it * day, DbContract.RESULT_HARD)  }

        // Zin — 2 days streak
        repeat(3) { seedSession(db, zinId, now - it * day, DbContract.RESULT_KNOWN) }
        repeat(1) { seedSession(db, zinId, now - it * day, DbContract.RESULT_HARD)  }

        // Leo — 6 days streak, second highest
        repeat(7) { seedSession(db, leoId, now - it * day, DbContract.RESULT_KNOWN) }
        repeat(3) { seedSession(db, leoId, now - it * day, DbContract.RESULT_HARD)  }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN PREMIUM SEED HELPER
    // ─────────────────────────────────────────────────────────────────────────

    fun adminEnsurePremiumSeedContent(db: SQLiteDatabase): Int {
        var count = 0
        db.beginTransaction()
        try {
            listOf("shoon@gmail.com", "nora@gmail.com", "alex@gmail.com", "leo@gmail.com").forEach { email ->
                val uid = getUserId(db, email)
                premiumDecks(uid).forEach { b -> if (ensurePremiumDeck(db, uid, b)) count++ }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PREMIUM DECK DEFINITIONS
    // ─────────────────────────────────────────────────────────────────────────

    private fun premiumDecks(ownerId: Long): List<SeedBundle> = listOf(
        SeedBundle("Premium SQL Essentials", "Higher-value SQL queries and database concepts.", listOf(
            "GROUP BY"   to "Groups rows for aggregation",
            "HAVING"     to "Filters grouped results",
            "INDEX"      to "Improves query lookup speed",
            "TRANSACTION"to "A sequence of operations as one unit",
            "VIEW"       to "A stored query used as a virtual table",
            "TRIGGER"    to "Action run automatically on DB events"
        )),
        SeedBundle("Premium Kotlin Patterns", "Important Kotlin concepts for Android learners.", listOf(
            "data class"     to "Class mainly used to hold data",
            "sealed class"   to "Restricted class hierarchy",
            "lazy"           to "Initializes value only when needed",
            "when"           to "Powerful conditional expression",
            "extension fun"  to "Adds new function to existing class",
            "coroutine"      to "Lightweight concurrent execution block"
        )),
        SeedBundle("Premium Android UI", "Useful concepts for better Android UI building.", listOf(
            "RecyclerView"     to "Displays scrollable lists efficiently",
            "ViewBinding"      to "Type-safe access to layout views",
            "ConstraintLayout" to "Flexible layout with constraints",
            "MaterialCardView" to "Card-style container using Material Design",
            "BottomNavigation" to "Tab-based navigation at screen bottom",
            "Snackbar"         to "Short message shown at bottom of screen"
        )),
        SeedBundle("Premium UX Heuristics", "Common usability concepts for better design thinking.", listOf(
            "Visibility of status" to "Keep users informed",
            "Consistency"          to "Use familiar patterns and behavior",
            "Error prevention"     to "Design to reduce mistakes",
            "User control"         to "Let users undo or exit actions",
            "Minimalist design"    to "Remove anything that is not useful",
            "Help and docs"        to "Provide easy-to-search documentation"
        )),
        SeedBundle("Premium Biology Concepts", "Biology revision with higher-value core concepts.", listOf(
            "Osmosis"       to "Movement of water across a membrane",
            "Diffusion"     to "Particles move from high to low concentration",
            "Enzyme"        to "Protein that speeds up reactions",
            "Homeostasis"   to "Maintaining internal stability",
            "Meiosis"       to "Cell division to produce sex cells",
            "Mitosis"       to "Cell division for growth and repair"
        )),
        SeedBundle("Premium Networking Security", "Networking and security starter concepts.", listOf(
            "Firewall"  to "Filters network traffic",
            "VPN"       to "Encrypted connection over a public network",
            "TLS"       to "Protects data in transit",
            "Phishing"  to "Fake communication used to steal information",
            "HTTPS"     to "Secure version of HTTP using TLS",
            "OAuth"     to "Open standard for access delegation"
        )),
        SeedBundle("Premium Exam Vocabulary", "Academic words useful for exams and essays.", listOf(
            "Analyze"    to "Examine something in detail",
            "Evaluate"   to "Judge value or quality",
            "Synthesize" to "Combine ideas into a whole",
            "Interpret"  to "Explain meaning",
            "Justify"    to "Give reasons to support a view",
            "Discuss"    to "Explore different perspectives"
        ))
    )

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL MODELS
    // ─────────────────────────────────────────────────────────────────────────

    private data class SeedBundle(val title: String, val description: String, val cards: List<Pair<String, String>>)

    // ─────────────────────────────────────────────────────────────────────────
    // DATABASE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private fun getUserId(db: SQLiteDatabase, email: String): Long {
        db.rawQuery(
            "SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS} WHERE ${DbContract.U_EMAIL}=? LIMIT 1",
            arrayOf(email.trim().lowercase())
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return -1L
    }

    private fun ensureUserId(
        db: SQLiteDatabase, name: String, email: String, password: String,
        role: String, forcePwChange: Boolean, isPremiumUser: Boolean
    ): Long {
        val normalized = email.trim().lowercase()
        db.rawQuery(
            "SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS} WHERE ${DbContract.U_EMAIL}=? LIMIT 1",
            arrayOf(normalized)
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        val cv = ContentValues().apply {
            put(DbContract.U_NAME,          name.trim())
            put(DbContract.U_EMAIL,         normalized)
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password.toCharArray()))
            put(DbContract.U_ROLE,          role)
            put(DbContract.U_STATUS,        DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS,1)
            put(DbContract.U_FORCE_PW_CHANGE, if (forcePwChange) 1 else 0)
            put(DbContract.U_CREATED_AT,    System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, if (isPremiumUser) 1 else 0)
        }
        return db.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    private fun ensureCategory(db: SQLiteDatabase, name: String, description: String, sortOrder: Int): Long {
        db.rawQuery(
            "SELECT ${DbContract.CAT_ID} FROM ${DbContract.T_CATEGORIES} WHERE ${DbContract.CAT_NAME}=? COLLATE NOCASE LIMIT 1",
            arrayOf(name)
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        val cv = ContentValues().apply {
            put(DbContract.CAT_NAME,        name)
            put(DbContract.CAT_DESCRIPTION, description)
            put(DbContract.CAT_IS_ACTIVE,   1)
            put(DbContract.CAT_SORT_ORDER,  sortOrder)
            put(DbContract.CAT_CREATED_AT,  System.currentTimeMillis())
        }
        return db.insertOrThrow(DbContract.T_CATEGORIES, null, cv)
    }

    private fun ensureSubject(db: SQLiteDatabase, categoryId: Long, name: String, description: String, sortOrder: Int) {
        db.rawQuery(
            "SELECT ${DbContract.SUBJ_ID} FROM ${DbContract.T_SUBJECTS} WHERE ${DbContract.SUBJ_NAME}=? COLLATE NOCASE LIMIT 1",
            arrayOf(name)
        ).use { c -> if (c.moveToFirst()) return }
        val cv = ContentValues().apply {
            put(DbContract.SUBJ_CATEGORY_ID, categoryId)
            put(DbContract.SUBJ_NAME,        name)
            put(DbContract.SUBJ_DESCRIPTION, description)
            put(DbContract.SUBJ_IS_ACTIVE,   1)
            put(DbContract.SUBJ_SORT_ORDER,  sortOrder)
            put(DbContract.SUBJ_CREATED_AT,  System.currentTimeMillis())
        }
        db.insertOrThrow(DbContract.T_SUBJECTS, null, cv)
    }

    private fun ensureLanguage(db: SQLiteDatabase, name: String, description: String, sortOrder: Int) {
        db.rawQuery(
            "SELECT ${DbContract.LANG_ID} FROM ${DbContract.T_LANGUAGES} WHERE ${DbContract.LANG_NAME}=? COLLATE NOCASE LIMIT 1",
            arrayOf(name)
        ).use { c -> if (c.moveToFirst()) return }
        val cv = ContentValues().apply {
            put(DbContract.LANG_NAME,        name)
            put(DbContract.LANG_DESCRIPTION, description)
            put(DbContract.LANG_IS_ACTIVE,   1)
            put(DbContract.LANG_SORT_ORDER,  sortOrder)
            put(DbContract.LANG_CREATED_AT,  System.currentTimeMillis())
        }
        db.insertOrThrow(DbContract.T_LANGUAGES, null, cv)
    }

    private fun ensureAchievement(db: SQLiteDatabase, title: String, description: String, metricKey: String, targetValue: Int, sortOrder: Int) {
        db.rawQuery(
            "SELECT ${DbContract.A_ID} FROM ${DbContract.T_ACHIEVEMENTS} WHERE ${DbContract.A_TITLE}=? COLLATE NOCASE LIMIT 1",
            arrayOf(title)
        ).use { c -> if (c.moveToFirst()) return }
        val cv = ContentValues().apply {
            put(DbContract.A_TITLE,        title)
            put(DbContract.A_DESCRIPTION,  description)
            put(DbContract.A_METRIC_KEY,   metricKey)
            put(DbContract.A_TARGET_VALUE, targetValue)
            put(DbContract.A_IS_ACTIVE,    1)
            put(DbContract.A_SORT_ORDER,   sortOrder)
            put(DbContract.A_CREATED_AT,   System.currentTimeMillis())
        }
        db.insertOrThrow(DbContract.T_ACHIEVEMENTS, null, cv)
    }

    private fun ensureSubscriptionPlan(db: SQLiteDatabase, name: String, priceText: String, durationDays: Int, description: String, sortOrder: Int) {
        db.rawQuery(
            "SELECT ${DbContract.SP_ID} FROM ${DbContract.T_SUBSCRIPTION_PLANS} WHERE ${DbContract.SP_NAME}=? COLLATE NOCASE LIMIT 1",
            arrayOf(name)
        ).use { c -> if (c.moveToFirst()) return }
        val cv = ContentValues().apply {
            put(DbContract.SP_NAME,         name)
            put(DbContract.SP_PRICE_TEXT,   priceText)
            put(DbContract.SP_DURATION_DAYS,durationDays)
            put(DbContract.SP_DESCRIPTION,  description)
            put(DbContract.SP_IS_ACTIVE,    1)
            put(DbContract.SP_SORT_ORDER,   sortOrder)
            put(DbContract.SP_CREATED_AT,   System.currentTimeMillis())
        }
        db.insertOrThrow(DbContract.T_SUBSCRIPTION_PLANS, null, cv)
    }

    private fun ensureDeckWithCards(
        db: SQLiteDatabase, ownerUserId: Long, title: String,
        description: String, isPublic: Boolean, cards: List<Pair<String, String>>
    ) {
        val existingId = db.rawQuery(
            "SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_OWNER_USER_ID}=? AND ${DbContract.D_TITLE}=? LIMIT 1",
            arrayOf(ownerUserId.toString(), title)
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }

        val deckId = if (existingId > 0L) {
            db.update(DbContract.T_DECKS, ContentValues().apply {
                put(DbContract.D_DESCRIPTION, description)
                put(DbContract.D_STATUS,      DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM,  0)
                put(DbContract.D_IS_PUBLIC,   if (isPublic) 1 else 0)
            }, "${DbContract.D_ID}=?", arrayOf(existingId.toString()))
            existingId
        } else {
            db.insertOrThrow(DbContract.T_DECKS, null, ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, ownerUserId)
                put(DbContract.D_TITLE,         title)
                put(DbContract.D_DESCRIPTION,   description)
                put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
                put(DbContract.D_STATUS,        DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM,    0)
                put(DbContract.D_IS_PUBLIC,     if (isPublic) 1 else 0)
            })
        }
        cards.forEach { (front, back) -> ensureCard(db, deckId, front, back) }
    }

    private fun ensurePremiumDeck(db: SQLiteDatabase, ownerUserId: Long, bundle: SeedBundle): Boolean {
        val existingId = db.rawQuery(
            "SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_OWNER_USER_ID}=? AND ${DbContract.D_TITLE}=? LIMIT 1",
            arrayOf(ownerUserId.toString(), bundle.title)
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }

        val deckId: Long
        val inserted: Boolean
        if (existingId > 0L) {
            deckId   = existingId
            inserted = false
            db.update(DbContract.T_DECKS, ContentValues().apply {
                put(DbContract.D_DESCRIPTION, bundle.description)
                put(DbContract.D_STATUS,      DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM,  1)
                put(DbContract.D_IS_PUBLIC,   1)
            }, "${DbContract.D_ID}=?", arrayOf(existingId.toString()))
        } else {
            inserted = true
            deckId   = db.insertOrThrow(DbContract.T_DECKS, null, ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, ownerUserId)
                put(DbContract.D_TITLE,         bundle.title)
                put(DbContract.D_DESCRIPTION,   bundle.description)
                put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
                put(DbContract.D_STATUS,        DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM,    1)
                put(DbContract.D_IS_PUBLIC,     1)
            })
        }
        bundle.cards.forEach { (front, back) -> ensureCard(db, deckId, front, back) }
        return inserted
    }

    private fun ensureCard(db: SQLiteDatabase, deckId: Long, front: String, back: String) {
        val exists = db.rawQuery(
            "SELECT 1 FROM ${DbContract.T_CARDS} WHERE ${DbContract.C_DECK_ID}=? AND ${DbContract.C_FRONT}=? LIMIT 1",
            arrayOf(deckId.toString(), front)
        ).use { c -> c.moveToFirst() }
        if (exists) return
        db.insertOrThrow(DbContract.T_CARDS, null, ContentValues().apply {
            put(DbContract.C_DECK_ID,    deckId)
            put(DbContract.C_FRONT,      front)
            put(DbContract.C_BACK,       back)
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        })
    }

    private fun ensureFriendship(db: SQLiteDatabase, userId: Long, friendId: Long, status: String) {
        if (userId < 0 || friendId < 0) return
        val exists = db.rawQuery(
            """SELECT 1 FROM ${DbContract.T_FRIENDSHIPS}
               WHERE (${DbContract.F_ID} = ? AND ${DbContract.F_STATUS} IS NOT NULL)
                  OR (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?) LIMIT 1""",
            arrayOf("0", userId.toString(), friendId.toString(), friendId.toString(), userId.toString())
        ).use { c -> c.moveToFirst() }
        if (exists) return
        db.insertOrThrow(DbContract.T_FRIENDSHIPS, null, ContentValues().apply {
            put("user_id",    userId)
            put("friend_id",  friendId)
            put(DbContract.F_STATUS,     status)
            put(DbContract.F_CREATED_AT, System.currentTimeMillis())
        })
    }

    private fun seedSession(db: SQLiteDatabase, userId: Long, atTime: Long, result: String) {
        if (userId < 0) return
        val deckId = db.rawQuery(
            "SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_OWNER_USER_ID}=? LIMIT 1",
            arrayOf(userId.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else return }
        db.insertOrThrow(DbContract.T_STUDY_SESSIONS, null, ContentValues().apply {
            put(DbContract.S_USER_ID,   userId)
            put(DbContract.S_DECK_ID,   deckId)
            put(DbContract.S_RESULT,    result)
            put(DbContract.S_CREATED_AT, atTime)
        })
    }

    private fun ensureReportReason(db: SQLiteDatabase, name: String, description: String, sortOrder: Int): Long {
        db.rawQuery(
            "SELECT ${DbContract.RR_ID} FROM ${DbContract.T_REPORT_REASONS} WHERE ${DbContract.RR_NAME}=? COLLATE NOCASE LIMIT 1",
            arrayOf(name.trim())
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        val cv = ContentValues().apply {
            put(DbContract.RR_NAME,        name.trim())
            put(DbContract.RR_DESCRIPTION, description.trim())
            put(DbContract.RR_IS_ACTIVE,   1)
            put(DbContract.RR_SORT_ORDER,  sortOrder)
            put(DbContract.RR_CREATED_AT,  System.currentTimeMillis())
        }
        return db.insertOrThrow(DbContract.T_REPORT_REASONS, null, cv)
    }

    // Legacy aliases kept so StarDeckDbHelper.kt doesn't need edits
    private fun ensureSeedUserId(db: SQLiteDatabase, name: String, email: String, password: String, role: String): Long =
        ensureUserId(db, name, email, password, role, forcePwChange = false, isPremiumUser = false)

    private fun ensureSeedCard(db: SQLiteDatabase, deckId: Long, front: String, back: String) =
        ensureCard(db, deckId, front, back)
}
