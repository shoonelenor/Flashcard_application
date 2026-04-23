package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.stardeckapplication.util.PasswordHasher

/**
 * SeederDao: all demo / initial data seeding for StarDeck.
 *
 * Call from StarDeckDbHelper:
 *
 * override fun onCreate(db: SQLiteDatabase) {
 *     SchemaDao.createAllTables(db)
 *     SeederDao.seedStaffAccounts(db)
 *     SeederDao.seedDemoDecksAndCards(db)
 * }
 *
 * override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
 *     MigrationDao.migrate(db, oldVersion, newVersion)
 *     SeederDao.seedStaffAccounts(db)
 *     SeederDao.seedDemoDecksAndCards(db)
 * }
 */
object DbSeeder {

    fun seedReportReasons(db: SQLiteDatabase) {
        // These reasons are for the Help / Report Issues (trouble ticket) system.
        // They are NOT for content reports (reporting a deck/flashcard).
        ensureReportReason(
            db = db,
            name = "Bug / App Crash",
            description = "The app crashed, froze, or something stopped working unexpectedly.",
            sortOrder = 10
        )
        ensureReportReason(
            db = db,
            name = "Feature Not Working",
            description = "A feature exists but is not behaving correctly or is broken.",
            sortOrder = 20
        )
        ensureReportReason(
            db = db,
            name = "Login / Account Issue",
            description = "Problems with signing in, password, or account access.",
            sortOrder = 30
        )
        ensureReportReason(
            db = db,
            name = "Study / Flashcard Problem",
            description = "Issues with the study session, card flipping, or progress not saving.",
            sortOrder = 40
        )
        ensureReportReason(
            db = db,
            name = "Deck or Card Not Loading",
            description = "Decks or cards are missing, not loading, or showing blank content.",
            sortOrder = 50
        )
        ensureReportReason(
            db = db,
            name = "Subscription / Payment Issue",
            description = "Problems with premium subscription, billing, or unlocking premium content.",
            sortOrder = 60
        )
        ensureReportReason(
            db = db,
            name = "Performance / Speed Issue",
            description = "The app is slow, laggy, or takes too long to load.",
            sortOrder = 70
        )
        ensureReportReason(
            db = db,
            name = "UI / Display Problem",
            description = "Something looks visually wrong, overlapping, or hard to read.",
            sortOrder = 80
        )
        ensureReportReason(
            db = db,
            name = "Suggestion / Feedback",
            description = "I have an idea or general feedback about the application.",
            sortOrder = 90
        )
        ensureReportReason(
            db = db,
            name = "Other",
            description = "Something else not listed above.",
            sortOrder = 100
        )
    }

    private fun ensureReportReason(
        db: SQLiteDatabase,
        name: String,
        description: String,
        sortOrder: Int
    ): Long {
        val cleanName = name.trim()

        // If it already exists, return its id — do not duplicate
        db.rawQuery(
            """
            SELECT ${DbContract.RR_ID}
            FROM ${DbContract.T_REPORT_REASONS}
            WHERE ${DbContract.RR_NAME} = ?
            COLLATE NOCASE
            LIMIT 1
            """.trimIndent(),
            arrayOf(cleanName)
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val cv = ContentValues().apply {
            put(DbContract.RR_NAME, cleanName)
            put(DbContract.RR_DESCRIPTION, description.trim())
            put(DbContract.RR_IS_ACTIVE, 1)
            put(DbContract.RR_SORT_ORDER, sortOrder)
            put(DbContract.RR_CREATED_AT, System.currentTimeMillis())
        }
        return db.insertOrThrow(DbContract.T_REPORT_REASONS, null, cv)
    }

    // ---------- PUBLIC API ----------

    fun seedStaffAccounts(db: SQLiteDatabase) {
        // Admin + Manager + 2 demo users (Shoon, Nora)
        ensureUserId(
            db = db,
            name = "Admin",
            email = "admin@stardeck.local",
            password = "Admin1234",
            role = DbContract.ROLE_ADMIN,
            forcePwChange = true,
            isPremiumUser = false
        )
        ensureUserId(
            db = db,
            name = "Manager",
            email = "manager@stardeck.local",
            password = "Manager1234",
            role = DbContract.ROLE_MANAGER,
            forcePwChange = true,
            isPremiumUser = false
        )
        ensureUserId(
            db = db,
            name = "Shoon",
            email = "shoon@gmail.com",
            password = "shoon1234",
            role = DbContract.ROLE_USER,
            forcePwChange = false,
            isPremiumUser = false
        )
        ensureUserId(
            db = db,
            name = "Nora",
            email = "nora@gmail.com",
            password = "nora1234",
            role = DbContract.ROLE_USER,
            forcePwChange = false,
            isPremiumUser = false
        )
    }

    fun seedDemoDecksAndCards(db: SQLiteDatabase) {
        val shoonId = ensureUserId(
            db = db,
            name = "Shoon",
            email = "shoon@gmail.com",
            password = "shoon1234",
            role = DbContract.ROLE_USER,
            forcePwChange = false,
            isPremiumUser = false
        )
        val noraId = ensureUserId(
            db = db,
            name = "Nora",
            email = "nora@gmail.com",
            password = "nora1234",
            role = DbContract.ROLE_USER,
            forcePwChange = false,
            isPremiumUser = false
        )

        val shoonDecks = listOf(
            SeedDeck(
                title = "English Verbs",
                description = "Common verbs for daily communication.",
                isPublic = true,
                cards = listOf(
                    "Go" to "To move from one place to another",
                    "Eat" to "To consume food",
                    "Read" to "To look at and understand words",
                    "Write" to "To form letters or words",
                    "Speak" to "To say words aloud",
                    "Learn" to "To gain knowledge or skill"
                )
            ),
            SeedDeck(
                title = "Basic SQL",
                description = "Starter queries and key terms for SQL.",
                isPublic = true,
                cards = listOf(
                    "SELECT" to "Used to retrieve data from a table",
                    "WHERE" to "Used to filter rows",
                    "INSERT" to "Used to add new rows",
                    "UPDATE" to "Used to modify existing rows",
                    "DELETE" to "Used to remove rows",
                    "PRIMARY KEY" to "Unique identifier for each record"
                )
            ),
            SeedDeck(
                title = "Biology Basics",
                description = "Quick biology revision deck.",
                isPublic = false,
                cards = listOf(
                    "Cell" to "Basic unit of life",
                    "Nucleus" to "Controls activities of the cell",
                    "Mitochondria" to "Produces energy for the cell",
                    "Tissue" to "Group of similar cells",
                    "Organ" to "Structure made of multiple tissues",
                    "Photosynthesis" to "Process plants use to make food"
                )
            ),
            SeedDeck(
                title = "Agile Terms",
                description = "Common terms used in agile projects.",
                isPublic = true,
                cards = listOf(
                    "Sprint" to "A short time-boxed period of development",
                    "Backlog" to "Ordered list of work items",
                    "Scrum Master" to "Facilitates the scrum process",
                    "User Story" to "Short description of a feature from a user view",
                    "Stand-up" to "Short daily team meeting",
                    "Retrospective" to "Meeting to reflect and improve"
                )
            ),
            SeedDeck(
                title = "Japanese N5",
                description = "Starter Japanese vocabulary.",
                isPublic = false,
                cards = listOf(
                    "みず (mizu)" to "Water",
                    "ひ (hi)" to "Fire",
                    "やま (yama)" to "Mountain",
                    "かわ (kawa)" to "River",
                    "がっこう (gakkou)" to "School",
                    "せんせい (sensei)" to "Teacher"
                )
            )
        )

        val noraDecks = listOf(
            SeedDeck(
                title = "World Capitals",
                description = "Countries and their capitals.",
                isPublic = true,
                cards = listOf(
                    "France" to "Paris",
                    "Japan" to "Tokyo",
                    "Thailand" to "Bangkok",
                    "Australia" to "Canberra",
                    "Canada" to "Ottawa",
                    "Brazil" to "Brasilia"
                )
            ),
            SeedDeck(
                title = "Math Formulas",
                description = "Useful formulas for quick revision.",
                isPublic = false,
                cards = listOf(
                    "Area of rectangle" to "length × width",
                    "Area of triangle" to "1/2 × base × height",
                    "Perimeter of square" to "4 × side",
                    "Circumference of circle" to "2 × π × r",
                    "Pythagoras" to "a² + b² = c²",
                    "Average" to "sum ÷ count"
                )
            ),
            SeedDeck(
                title = "UI UX Terms",
                description = "Key design and usability terms.",
                isPublic = true,
                cards = listOf(
                    "Wireframe" to "Basic layout of a screen",
                    "Prototype" to "Interactive model of a design",
                    "Usability" to "How easy a product is to use",
                    "Consistency" to "Similar elements behave the same way",
                    "Accessibility" to "Design usable by more people",
                    "Feedback" to "System response to user action"
                )
            ),
            SeedDeck(
                title = "Human Anatomy",
                description = "Simple anatomy revision deck.",
                isPublic = false,
                cards = listOf(
                    "Heart" to "Pumps blood through the body",
                    "Lungs" to "Help with breathing",
                    "Brain" to "Controls body functions",
                    "Femur" to "Longest bone in the body",
                    "Skin" to "Largest organ of the body",
                    "Rib cage" to "Protects the heart and lungs"
                )
            ),
            SeedDeck(
                title = "Networking Basics",
                description = "Starter concepts for computer networking.",
                isPublic = true,
                cards = listOf(
                    "IP Address" to "Unique address for a device on a network",
                    "Router" to "Connects networks and forwards data",
                    "Switch" to "Connects devices in a local network",
                    "LAN" to "Local Area Network",
                    "WAN" to "Wide Area Network",
                    "DNS" to "Translates domain names to IP addresses"
                )
            )
        )

        shoonDecks.forEach { deck ->
            ensureDeckWithCards(
                db = db,
                ownerUserId = shoonId,
                title = deck.title,
                description = deck.description,
                isPublic = deck.isPublic,
                cards = deck.cards
            )
        }

        noraDecks.forEach { deck ->
            ensureDeckWithCards(
                db = db,
                ownerUserId = noraId,
                title = deck.title,
                description = deck.description,
                isPublic = deck.isPublic,
                cards = deck.cards
            )
        }

        // Seed premium demo bundles as well
        premiumSeedDecksForShoon().forEach { bundle ->
            ensureSeedPremiumDeck(db, shoonId, bundle)
        }
        premiumSeedDecksForNora().forEach { bundle ->
            ensureSeedPremiumDeck(db, noraId, bundle)
        }
    }

    /**
     * Used by admin to make sure premium seed content exists.
     * Returns how many premium decks were newly inserted.
     */
    fun adminEnsurePremiumSeedContent(db: SQLiteDatabase): Int {
        var insertedDecks = 0
        db.beginTransaction()
        try {
            val shoonId = ensureSeedUserId(
                db = db,
                name = "Shoon",
                email = "shoon@gmail.com",
                password = "shoon1234",
                role = DbContract.ROLE_USER
            )
            val noraId = ensureSeedUserId(
                db = db,
                name = "Nora",
                email = "nora@gmail.com",
                password = "nora1234",
                role = DbContract.ROLE_USER
            )

            premiumSeedDecksForShoon().forEach { bundle ->
                if (ensureSeedPremiumDeck(db, shoonId, bundle)) {
                    insertedDecks++
                }
            }
            premiumSeedDecksForNora().forEach { bundle ->
                if (ensureSeedPremiumDeck(db, noraId, bundle)) {
                    insertedDecks++
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedDecks
    }

    // ---------- INTERNAL MODELS ----------

    private data class SeedDeck(
        val title: String,
        val description: String,
        val isPublic: Boolean,
        val cards: List<Pair<String, String>>
    )

    private data class SeedDeckBundle(
        val title: String,
        val description: String,
        val cards: List<Pair<String, String>>
    )

    // ---------- USER HELPERS ----------

    private fun ensureSeedUserId(
        db: SQLiteDatabase,
        name: String,
        email: String,
        password: String,
        role: String
    ): Long {
        val normalized = email.trim().lowercase()
        db.rawQuery(
            "SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS} WHERE ${DbContract.U_EMAIL}=? LIMIT 1",
            arrayOf(normalized)
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, normalized)
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password.toCharArray()))
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS, 1)
            put(DbContract.U_FORCE_PW_CHANGE, 0)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, 0)
        }
        return db.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    private fun ensureUserId(
        db: SQLiteDatabase,
        name: String,
        email: String,
        password: String,
        role: String,
        forcePwChange: Boolean,
        isPremiumUser: Boolean
    ): Long {
        val normalized = email.trim().lowercase()
        db.rawQuery(
            "SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS} WHERE ${DbContract.U_EMAIL}=? LIMIT 1",
            arrayOf(normalized)
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, normalized)
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password.toCharArray()))
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS, 1)
            put(DbContract.U_FORCE_PW_CHANGE, if (forcePwChange) 1 else 0)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, if (isPremiumUser) 1 else 0)
        }
        return db.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    // ---------- PREMIUM SEED HELPERS ----------

    private fun ensureSeedPremiumDeck(
        db: SQLiteDatabase,
        ownerUserId: Long,
        bundle: SeedDeckBundle
    ): Boolean {
        val existingDeckId = db.rawQuery(
            "SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_OWNER_USER_ID}=? AND ${DbContract.D_TITLE}=? LIMIT 1",
            arrayOf(ownerUserId.toString(), bundle.title)
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }

        val deckId: Long
        val insertedNewDeck: Boolean

        if (existingDeckId > 0L) {
            deckId = existingDeckId
            insertedNewDeck = false
            val cv = ContentValues().apply {
                put(DbContract.D_DESCRIPTION, bundle.description)
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM, 1)
                put(DbContract.D_IS_PUBLIC, 1)
            }
            db.update(
                DbContract.T_DECKS,
                cv,
                "${DbContract.D_ID}=?",
                arrayOf(deckId.toString())
            )
        } else {
            insertedNewDeck = true
            deckId = db.insertOrThrow(
                DbContract.T_DECKS,
                null,
                ContentValues().apply {
                    put(DbContract.D_OWNER_USER_ID, ownerUserId)
                    put(DbContract.D_TITLE, bundle.title)
                    put(DbContract.D_DESCRIPTION, bundle.description)
                    put(DbContract.D_CREATED_AT, System.currentTimeMillis())
                    put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                    put(DbContract.D_IS_PREMIUM, 1)
                    put(DbContract.D_IS_PUBLIC, 1)
                }
            )
        }

        bundle.cards.forEach { (front, back) ->
            ensureSeedCard(db, deckId, front, back)
        }

        return insertedNewDeck
    }

    private fun ensureSeedCard(
        db: SQLiteDatabase,
        deckId: Long,
        front: String,
        back: String
    ) {
        val exists = db.rawQuery(
            "SELECT 1 FROM ${DbContract.T_CARDS} WHERE ${DbContract.C_DECK_ID}=? AND ${DbContract.C_FRONT}=? LIMIT 1",
            arrayOf(deckId.toString(), front)
        ).use { c ->
            c.moveToFirst()
        }
        if (exists) return

        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId)
            put(DbContract.C_FRONT, front)
            put(DbContract.C_BACK, back)
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        db.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    private fun premiumSeedDecksForShoon(): List<SeedDeckBundle> = listOf(
        SeedDeckBundle(
            title = "Premium English Idioms",
            description = "Advanced idioms for fluent English communication.",
            cards = listOf(
                "Break the ice" to "Start a conversation in a relaxed way",
                "Hit the sack" to "Go to sleep",
                "Piece of cake" to "Very easy",
                "Under the weather" to "Feeling unwell"
            )
        ),
        SeedDeckBundle(
            title = "Premium SQL Essentials",
            description = "Higher-value SQL queries and database concepts.",
            cards = listOf(
                "JOIN" to "Combines rows from related tables",
                "GROUP BY" to "Groups rows for aggregation",
                "HAVING" to "Filters grouped results",
                "INDEX" to "Improves query lookup speed"
            )
        ),
        SeedDeckBundle(
            title = "Premium Kotlin Patterns",
            description = "Important Kotlin concepts for Android learners.",
            cards = listOf(
                "data class" to "Class mainly used to hold data",
                "sealed class" to "Restricted class hierarchy",
                "lazy" to "Initializes value only when needed",
                "when" to "Powerful conditional expression"
            )
        ),
        SeedDeckBundle(
            title = "Premium Android UI",
            description = "Useful concepts for better Android interface building.",
            cards = listOf(
                "RecyclerView" to "Displays scrollable lists efficiently",
                "ViewBinding" to "Type-safe access to layout views",
                "Fragment" to "Reusable part of UI in an activity",
                "MaterialCardView" to "Card-style container using Material Design"
            )
        ),
        SeedDeckBundle(
            title = "Premium UX Heuristics",
            description = "Common usability concepts for better design thinking.",
            cards = listOf(
                "Visibility of status" to "Keep users informed",
                "Consistency" to "Use familiar patterns and behavior",
                "Error prevention" to "Design to reduce mistakes",
                "User control" to "Let users undo or exit actions"
            )
        ),
        SeedDeckBundle(
            title = "Premium Biology Concepts",
            description = "Biology revision deck with higher-value core concepts.",
            cards = listOf(
                "Osmosis" to "Movement of water across a membrane",
                "Diffusion" to "Particles move from high to low concentration",
                "Enzyme" to "Protein that speeds up reactions",
                "Homeostasis" to "Maintaining internal stability"
            )
        ),
        SeedDeckBundle(
            title = "Premium Japanese Starter",
            description = "Beginner Japanese words and meanings.",
            cards = listOf(
                "ともだち (tomodachi)" to "Friend",
                "じかん (jikan)" to "Time",
                "べんきょう (benkyou)" to "Study",
                "でんしゃ (densha)" to "Train"
            )
        ),
        SeedDeckBundle(
            title = "Premium Agile Delivery",
            description = "Agile concepts for project and software teams.",
            cards = listOf(
                "Sprint Goal" to "Main objective for the sprint",
                "Velocity" to "Amount of work completed in a sprint",
                "Refinement" to "Clarifying and preparing backlog items",
                "Definition of Done" to "Quality checklist for completed work"
            )
        ),
        SeedDeckBundle(
            title = "Premium Networking Security",
            description = "Networking and security starter concepts.",
            cards = listOf(
                "Firewall" to "Filters network traffic",
                "VPN" to "Encrypted connection over a public network",
                "TLS" to "Protects data in transit",
                "Phishing" to "Fake communication used to steal information"
            )
        ),
        SeedDeckBundle(
            title = "Premium Exam Vocabulary",
            description = "Academic words useful for exams and essays.",
            cards = listOf(
                "Analyze" to "Examine something in detail",
                "Evaluate" to "Judge value or quality",
                "Synthesize" to "Combine ideas into a whole",
                "Interpret" to "Explain meaning"
            )
        )
    )

    private fun premiumSeedDecksForNora(): List<SeedDeckBundle> = listOf(
        SeedDeckBundle(
            title = "Premium World Capitals",
            description = "Country and capital revision with extra practice.",
            cards = listOf(
                "Germany" to "Berlin",
                "Italy" to "Rome",
                "South Korea" to "Seoul",
                "Argentina" to "Buenos Aires"
            )
        ),
        SeedDeckBundle(
            title = "Premium Math Revision",
            description = "Math formulas and quick rules for revision.",
            cards = listOf(
                "Quadratic formula" to "(-b ± √(b²-4ac)) / (2a)",
                "Slope" to "(y₂ - y₁) / (x₂ - x₁)",
                "Simple interest" to "(P × R × T) / 100",
                "Area of circle" to "π × r²"
            )
        ),
        SeedDeckBundle(
            title = "Premium Human Anatomy",
            description = "Core anatomy content for quick review.",
            cards = listOf(
                "Skull" to "Protects the brain",
                "Spine" to "Supports the body and protects the spinal cord",
                "Liver" to "Processes nutrients and detoxifies blood",
                "Kidney" to "Filters waste from blood"
            )
        ),
        SeedDeckBundle(
            title = "Premium Computer Terms",
            description = "Useful computer and IT concepts.",
            cards = listOf(
                "Algorithm" to "Step-by-step procedure to solve a problem",
                "Compiler" to "Translates code into machine-readable form",
                "Cache" to "Fast temporary storage",
                "API" to "Set of rules for software communication"
            )
        ),
        SeedDeckBundle(
            title = "Premium Business English",
            description = "Professional vocabulary for workplace communication.",
            cards = listOf(
                "Agenda" to "List of topics for a meeting",
                "Deadline" to "Final time limit for a task",
                "Proposal" to "Formal suggestion or plan",
                "Stakeholder" to "Person affected by a project or decision"
            )
        ),
        SeedDeckBundle(
            title = "Premium Data Structures",
            description = "Important programming data structure concepts.",
            cards = listOf(
                "Array" to "Ordered collection of items",
                "Stack" to "Last in, first out structure",
                "Queue" to "First in, first out structure",
                "Tree" to "Hierarchical node-based structure"
            )
        ),
        SeedDeckBundle(
            title = "Premium Chemistry Core",
            description = "Starter chemistry facts and definitions.",
            cards = listOf(
                "Atom" to "Smallest unit of an element",
                "Molecule" to "Two or more atoms bonded together",
                "pH" to "Measure of acidity or alkalinity",
                "Catalyst" to "Substance that speeds up a reaction"
            )
        ),
        SeedDeckBundle(
            title = "Premium History Dates",
            description = "Important dates for general history review.",
            cards = listOf(
                "1914" to "Start of World War I",
                "1918" to "End of World War I",
                "1939" to "Start of World War II",
                "1945" to "End of World War II"
            )
        ),
        SeedDeckBundle(
            title = "Premium Project Management",
            description = "Core project management language and ideas.",
            cards = listOf(
                "Scope" to "Boundaries of project work",
                "Risk" to "Possible event that can affect objectives",
                "Milestone" to "Important checkpoint in a project",
                "Deliverable" to "Output produced by the project"
            )
        ),
        SeedDeckBundle(
            title = "Premium Cybersecurity Basics",
            description = "Basic security terms for digital safety.",
            cards = listOf(
                "Malware" to "Software designed to harm a system",
                "Ransomware" to "Malware that locks data for payment",
                "2FA" to "Two-factor authentication",
                "Password manager" to "Tool for storing strong passwords securely"
            )
        )
    )

    // ---------- DECK + CARDS HELPER ----------

    private fun ensureDeckWithCards(
        db: SQLiteDatabase,
        ownerUserId: Long,
        title: String,
        description: String,
        isPublic: Boolean,
        cards: List<Pair<String, String>>
    ) {
        val existingDeckId = db.rawQuery(
            "SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS} " +
                    "WHERE ${DbContract.D_OWNER_USER_ID}=? AND ${DbContract.D_TITLE}=? LIMIT 1",
            arrayOf(ownerUserId.toString(), title)
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }

        val deckId: Long =
            if (existingDeckId > 0L) {
                val cv = ContentValues().apply {
                    put(DbContract.D_DESCRIPTION, description)
                    put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                    put(DbContract.D_IS_PREMIUM, 0)
                    put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
                }
                db.update(
                    DbContract.T_DECKS,
                    cv,
                    "${DbContract.D_ID}=?",
                    arrayOf(existingDeckId.toString())
                )
                existingDeckId
            } else {
                db.insertOrThrow(
                    DbContract.T_DECKS,
                    null,
                    ContentValues().apply {
                        put(DbContract.D_OWNER_USER_ID, ownerUserId)
                        put(DbContract.D_TITLE, title)
                        put(DbContract.D_DESCRIPTION, description)
                        put(DbContract.D_CREATED_AT, System.currentTimeMillis())
                        put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                        put(DbContract.D_IS_PREMIUM, 0)
                        put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
                    }
                )
            }

        cards.forEach { (front, back) ->
            ensureSeedCard(db, deckId, front, back)
        }
    }
}