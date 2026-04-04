package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserStudyBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.StatsDao
import com.example.stardeckapplication.db.StudyDao
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.ui.study.StudyActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class UserStudyFragment : Fragment(R.layout.fragment_user_study) {

    private var _b: FragmentUserStudyBinding? = null
    private val b get() = _b!!

    private val session  by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val statsDao by lazy { StatsDao(dbHelper) }
    private val studyDao by lazy { StudyDao(dbHelper) }
    private val userDao  by lazy { UserDao(dbHelper) }
    private val deckDao  by lazy { UserDeckDao(dbHelper) }
    private val cardDao  by lazy { CardDao(dbHelper) }

    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    private var isPremiumUser      = false
    private var recentDeckId       : Long? = null
    private var bestDueDeckId      : Long? = null
    private var totalDueAcrossUser = 0
    private var deckItems          : List<DeckStudyItem> = emptyList()

    private data class DeckStudyItem(
        val deck       : UserDeckDao.DeckRow,
        val totalCards : Int,
        val dueCount   : Int,
        val isRecent   : Boolean
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserStudyBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.btnContinueStudy.setOnClickListener    { openContinueStudy() }
        b.btnDueNow.setOnClickListener           { openBestDueStudy() }
        b.btnOpenLibraryStudy.setOnClickListener { (activity as? UserHomeActivity)?.openTab(R.id.nav_library) }
        b.btnRefreshStudy.setOnClickListener     { reload() }
        b.btnEmptyOpenLibrary.setOnClickListener { (activity as? UserHomeActivity)?.openTab(R.id.nav_library) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun reload() {
        val me = session.load() ?: return
        if (inFlight.getAndSet(true)) return

        executor.execute {
            val todayCount : Int     = try { statsDao.getTodayStudyCount(me.id) }      catch (e: Exception) { 0 }
            val streakDays : Int     = try { statsDao.getStudyStreakDays(me.id) }      catch (e: Exception) { 0 }
            val recent     : StatsDao.RecentDeckRow?   = try { statsDao.getRecentlyStudiedDeck(me.id) } catch (e: Exception) { null }
            val premium    : Boolean                   = try { userDao.isUserPremium(me.id) }           catch (e: Exception) { false }
            val decks      : List<UserDeckDao.DeckRow> = try { deckDao.getDecksForOwner(me.id) }        catch (e: Exception) { emptyList() }

            val recentId = recent?.deckId

            val items: List<DeckStudyItem> = decks.map { deck: UserDeckDao.DeckRow ->
                val totalCards : Int = try { cardDao.getCardCountForDeck(deck.id) }        catch (e: Exception) { 0 }
                val dueCount   : Int = try { studyDao.getDueCountForDeck(me.id, deck.id) } catch (e: Exception) { 0 }
                DeckStudyItem(
                    deck       = deck,
                    totalCards = totalCards,
                    dueCount   = dueCount,
                    isRecent   = recentId != null && recentId == deck.id
                )
            }.sortedWith(
                compareByDescending<DeckStudyItem> { it.dueCount > 0 }
                    .thenByDescending { it.dueCount }
                    .thenByDescending { it.isRecent }
                    .thenBy { it.deck.title.lowercase(Locale.getDefault()) }
            )

            val totalDue : Int   = items.sumOf { it.dueCount }
            val bestDue  : Long? = items
                .filter { it.dueCount > 0 && (!it.deck.isPremium || premium) }
                .maxWithOrNull(
                    compareBy<DeckStudyItem> { it.dueCount }
                        .thenBy { if (it.isRecent) 1 else 0 }
                        .thenBy { it.totalCards }
                )
                ?.deck?.id

            val recentTitle : String? = recent?.title
            val recentMeta  : String? = recent?.lastStudiedAt?.let { "Last studied ${formatTime(it)}" }

            postUi {
                isPremiumUser      = premium
                recentDeckId       = recentId
                bestDueDeckId      = bestDue
                totalDueAcrossUser = totalDue
                deckItems          = items
                bindTopArea(todayCount, streakDays, recentTitle, recentMeta)
                renderDeckCards(items)
                inFlight.set(false)
            }
        }
    }

    private fun bindTopArea(
        todayCount  : Int,
        streakDays  : Int,
        recentTitle : String?,
        recentMeta  : String?
    ) {
        b.tvStudySubtitle.text =
            "Continue your latest session or start the best due deck automatically."
        b.tvTodayCards.text = if (todayCount == 1) "1 card reviewed" else "$todayCount cards reviewed"
        b.tvStreakDays.text = if (streakDays == 1) "1 day" else "$streakDays days"

        if (recentTitle.isNullOrBlank()) {
            b.tvRecentDeck.text = "No recent study session"
            b.tvRecentMeta.text = "Start from any deck below."
        } else {
            b.tvRecentDeck.text = recentTitle
            b.tvRecentMeta.text = recentMeta ?: "Recent session found"
        }

        // ✅ RESTORED: original smart due queue message
        b.tvDueInfo.text = when {
            totalDueAcrossUser > 0 && bestDueDeckId != null -> {
                val best = deckItems.firstOrNull { it.deck.id == bestDueDeckId }
                if (best != null)
                    "$totalDueAcrossUser due card(s) found. \u201cDue Now\u201d will open ${best.deck.title} first."
                else
                    "$totalDueAcrossUser due card(s) found. \u201cDue Now\u201d will open the best deck automatically."
            }
            deckItems.isEmpty() -> "No decks available yet. Create a deck in Library first."
            else -> "No cards are due right now. \u201cDue Now\u201d will fall back to your recent or first deck."
        }

        b.btnContinueStudy.text = if (deckItems.isEmpty()) "Open Library" else "Continue Study"
        b.btnDueNow.isEnabled   = deckItems.isNotEmpty()
        b.btnDueNow.alpha       = if (deckItems.isNotEmpty()) 1f else 0.5f
    }

    private fun renderDeckCards(items: List<DeckStudyItem>) {
        b.deckListContainer.removeAllViews()
        val empty = items.isEmpty()
        b.groupEmptyDecks.visibility   = if (empty) View.VISIBLE else View.GONE
        b.deckListContainer.visibility = if (empty) View.GONE    else View.VISIBLE
        if (empty) return
        items.take(8).forEach { item -> b.deckListContainer.addView(createDeckCard(item)) }
    }

    private fun createDeckCard(item: DeckStudyItem): View {
        val context = requireContext()

        val primaryColor  = ContextCompat.getColor(context, R.color.stardeck_primary)
        val cardBgColor   = ContextCompat.getColor(context, R.color.stardeck_field_bg)
        val strokeColor   = ContextCompat.getColor(context, R.color.stardeck_field_stroke)
        val textPrimary   = ContextCompat.getColor(context, R.color.stardeck_text_primary)
        val textSecondary = ContextCompat.getColor(context, R.color.stardeck_text_secondary)
        val white         = ContextCompat.getColor(context, android.R.color.white)

        val card = MaterialCardView(context).apply {
            radius = dp(20).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(ColorStateList.valueOf(strokeColor))
            setCardBackgroundColor(cardBgColor)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        // Horizontal wrapper for accent bar + content
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Left accent bar
        val accent = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(primaryColor)
        }

        // Content
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(context).apply {
            text = item.deck.title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimary)
        }

        val descView = TextView(context).apply {
            text = item.deck.description?.takeIf { it.isNotBlank() } ?: "Open this deck in study mode."
            textSize = 13f
            setTextColor(textSecondary)
            setPadding(0, dp(6), 0, 0)
        }

        val metaView = TextView(context).apply {
            text = buildMetaText(item)
            textSize = 12f
            setTextColor(textSecondary)
            setPadding(0, dp(6), 0, dp(10))
        }

        val action = MaterialButton(context).apply {
            text = when {
                item.deck.isPremium && !isPremiumUser -> "Unlock Premium"
                item.dueCount > 0                    -> "Study Due"
                else                                 -> "Study"
            }
            isAllCaps = false
            cornerRadius = dp(24)
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setTextColor(white)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { openDeck(item.deck) }
        }

        content.addView(titleView)
        content.addView(descView)
        content.addView(metaView)
        content.addView(action)

        outer.addView(accent)
        outer.addView(content)
        card.addView(outer)
        card.setOnClickListener { openDeck(item.deck) }
        return card
    }

    private fun buildMetaText(item: DeckStudyItem): String {
        if (item.deck.isPremium && !isPremiumUser) return "Premium deck • Unlock required"
        val parts = mutableListOf<String>()
        parts += if (item.totalCards == 1) "1 card" else "${item.totalCards} cards"
        if (item.dueCount > 0)
            parts += if (item.dueCount == 1) "1 due now" else "${item.dueCount} due now"
        else
            parts += "No due cards"
        if (item.isRecent) parts += "Recent"
        return parts.joinToString(" • ")
    }

    private fun openContinueStudy() {
        if (deckItems.isEmpty()) {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            Snackbar.make(b.root, "No deck yet. Create one in Library first.", Snackbar.LENGTH_SHORT).show()
            return
        }
        val target = deckItems.firstOrNull { it.deck.id == recentDeckId }
            ?: deckItems.firstOrNull { it.dueCount > 0 && (!it.deck.isPremium || isPremiumUser) }
            ?: deckItems.firstOrNull { !it.deck.isPremium || isPremiumUser }
            ?: deckItems.first()
        openDeck(target.deck)
    }

    private fun openBestDueStudy() {
        if (deckItems.isEmpty()) {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_library)
            Snackbar.make(b.root, "No deck found. Create one in Library first.", Snackbar.LENGTH_SHORT).show()
            return
        }
        val bestDueItem = deckItems.firstOrNull { it.deck.id == bestDueDeckId }
        if (bestDueItem != null) {
            Snackbar.make(b.root, "Opening best due deck: ${bestDueItem.deck.title}", Snackbar.LENGTH_SHORT).show()
            openDeck(bestDueItem.deck)
            return
        }
        val fallback = deckItems.firstOrNull { it.deck.id == recentDeckId }
            ?: deckItems.firstOrNull { !it.deck.isPremium || isPremiumUser }
            ?: deckItems.first()
        Snackbar.make(b.root, "No due cards yet. Opening ${fallback.deck.title} in normal review.", Snackbar.LENGTH_SHORT).show()
        openDeck(fallback.deck)
    }

    private fun openDeck(deck: UserDeckDao.DeckRow) {
        if (deck.isPremium && !isPremiumUser) {
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deck.id)
            )
            return
        }
        startActivity(
            Intent(requireContext(), StudyActivity::class.java)
                .putExtra(StudyActivity.EXTRA_DECK_ID, deck.id)
        )
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun postUi(block: () -> Unit) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            if (!isAdded || _b == null) return@runOnUiThread
            block()
        }
    }
}