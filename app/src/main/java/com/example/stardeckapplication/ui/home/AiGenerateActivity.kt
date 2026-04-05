package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stardeckapplication.BuildConfig
import com.example.stardeckapplication.databinding.ActivityAiGenerateBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiGenerateActivity : AppCompatActivity() {

    private lateinit var b: ActivityAiGenerateBinding

    private val session by lazy { SessionManager(this) }
    private val dbHelper by lazy { StarDeckDbHelper(this) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }
    private val cardDao by lazy { CardDao(dbHelper) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }

    private var generatedCards: List<Pair<String, String>> = emptyList()

    // ─────────────────────────────────────────────────────────────
    // API key is read from local.properties → BuildConfig at build time.
    // To set up: add  GEMINI_API_KEY=AIzaSy...  to your local.properties file.
    // Get a free key at https://aistudio.google.com/app/apikey
    // ─────────────────────────────────────────────────────────────
    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY

    // gemini-2.0-flash was shut down March 31 2026 — upgraded to gemini-2.5-flash
    private val GEMINI_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAiGenerateBinding.inflate(layoutInflater)
        setContentView(b.root)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) { finish(); return }

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Generate"
        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnGenerate.setOnClickListener { attemptGenerate() }
        b.btnSave.setOnClickListener { attemptSave(me.id) }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ─────────────────────────────────────────────────────────────
    // Generate
    // ─────────────────────────────────────────────────────────────

    private fun attemptGenerate() {
        b.tilNotes.error = null
        val text = b.etNotes.text?.toString().orEmpty().trim()

        if (text.length < 10) {
            b.tilNotes.error = "Please paste a bit more text (at least a few sentences)"
            return
        }
        if (GEMINI_API_KEY.isBlank()) {
            Snackbar.make(
                b.root,
                "No API key found. Add GEMINI_API_KEY=AIzaSy... to your local.properties and rebuild.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        setLoading(true)
        b.tvPreview.visibility = View.GONE
        b.btnSave.visibility = View.GONE
        generatedCards = emptyList()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { callGemini(text) }
            setLoading(false)

            when {
                result == null -> Snackbar.make(
                    b.root,
                    "Generation failed — API key may be invalid, model unavailable, or no internet. Check Logcat tag: AiGenerate.",
                    Snackbar.LENGTH_LONG
                ).show()
                result.isEmpty() -> b.tilNotes.error =
                    "Gemini couldn't find flashcards — try more detailed notes."
                else -> {
                    generatedCards = result
                    val preview = generatedCards.take(3)
                        .joinToString("\n\n") { (f, bk) -> "Q: $f\nA: $bk" }
                    val suffix =
                        if (generatedCards.size > 3) "\n\n…and ${generatedCards.size - 3} more" else ""
                    b.tvPreview.text = "${generatedCards.size} cards generated:\n\n$preview$suffix"
                    b.tvPreview.visibility = View.VISIBLE
                    if (b.etDeckTitle.text.isNullOrBlank()) b.etDeckTitle.setText("AI Generated Deck")
                    b.btnSave.visibility = View.VISIBLE
                    b.btnGenerate.text = "Re-generate"
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Gemini HTTP call
    // ─────────────────────────────────────────────────────────────

    private fun callGemini(notes: String): List<Pair<String, String>>? {
        return try {
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", buildPrompt(notes)) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 2048)
                })
            }

            val conn = (URL(GEMINI_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

            val code = conn.responseCode
            if (code != 200) {
                // Log the real API error — open Logcat in Android Studio and filter by "AiGenerate"
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "no error body"
                android.util.Log.e("AiGenerate", "HTTP $code: $errorBody")
                return null   // null = show the "Generation failed" Snackbar, not "try more notes"
            }

            parseGeminiResponse(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            android.util.Log.e("AiGenerate", "callGemini exception: ${e.message}", e)
            null
        }
    }

    private fun buildPrompt(notes: String): String = """
You are a flashcard generator. Read the study notes and produce flashcards.

STRICT OUTPUT RULES:
- Return ONLY a raw JSON array.
- Do NOT use markdown, code fences, backticks, or any explanation.
- Start your entire response with [ and end with ]
- Each element: {"front": "question", "back": "answer"}
- Generate at least 5 cards if the notes are long enough.
- front max 100 chars, back max 200 chars.

NOTES:
${notes.take(4000)}
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────
    // Robust parser: strips ALL code fence variants, then finds
    // the first [ ... ] block in the remaining text.
    // ─────────────────────────────────────────────────────────────

    private fun parseGeminiResponse(json: String): List<Pair<String, String>> {
        return try {
            val rawText = JSONObject(json)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            val stripped = rawText
                .replace(Regex("""```[a-zA-Z]*\s*"""), "")
                .replace(Regex("""```\s*"""), "")
                .trim()

            val start = stripped.indexOf('[')
            val end = stripped.lastIndexOf(']')
            if (start == -1 || end == -1 || end <= start) return emptyList()

            val jsonArray = stripped.substring(start, end + 1)
            val arr = JSONArray(jsonArray)
            val cards = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val front = obj.optString("front", "").trim()
                val back = obj.optString("back", "").trim()
                if (front.isNotBlank() && back.isNotBlank()) cards.add(front to back)
            }
            cards
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Save
    // ─────────────────────────────────────────────────────────────

    private fun attemptSave(userId: Long) {
        b.tilDeckTitle.error = null
        val title = b.etDeckTitle.text?.toString().orEmpty().trim()
        if (title.isBlank()) { b.tilDeckTitle.error = "Deck title is required"; return }
        if (title.length > 40) { b.tilDeckTitle.error = "Max 40 characters"; return }
        if (generatedCards.isEmpty()) {
            Snackbar.make(b.root, "Generate cards first.", Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val deckId = deckDao.createDeck(userId, title, "AI Generated from notes", false, false)
                    if (deckId <= 0L) return@runCatching -1L to 0
                    var created = 0
                    for ((front, back) in generatedCards) {
                        val id = runCatching {
                            cardDao.createCardAny(deckId, front, back)
                        }.getOrDefault(-1L)
                        if (id > 0L) created++
                    }
                    achievementSync.syncForUser(userId)
                    deckId to created
                }.getOrDefault(-1L to 0)
            }
            val (deckId, created) = result
            if (deckId <= 0L) {
                Snackbar.make(
                    b.root,
                    "Could not create deck. Try a different title.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }
            Snackbar.make(b.root, "Deck created with $created card(s).", Snackbar.LENGTH_LONG).show()
            startActivity(
                Intent(this@AiGenerateActivity, DeckCardsActivity::class.java)
                    .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deckId)
            )
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        b.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        b.btnGenerate.isEnabled = !loading
        b.btnSave.isEnabled = !loading
        b.btnGenerate.text = when {
            loading -> "Generating…"
            generatedCards.isEmpty() -> "Generate Flashcards"
            else -> "Re-generate"
        }
    }
}
