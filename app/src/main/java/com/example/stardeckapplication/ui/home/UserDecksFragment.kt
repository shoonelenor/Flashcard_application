// app/src/main/java/com/example/stardeckapplication/ui/home/UserDecksFragment.kt
package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.DialogEditDeckBinding
import com.example.stardeckapplication.databinding.FragmentUserDecksBinding
import com.example.stardeckapplication.databinding.ItemDeckBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class UserDecksFragment : Fragment(R.layout.fragment_user_decks) {

    private var _b: FragmentUserDecksBinding? = null
    private val b get() = _b!!

    private val db by lazy { StarDeckDbHelper(requireContext()) }
    private val session by lazy { SessionManager(requireContext()) }

    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    private var isPremiumUser: Boolean = false
    private var all: List<StarDeckDbHelper.DeckRow> = emptyList()

    private var filterMode: FilterMode = FilterMode.ALL
    private var sortMode: SortMode = SortMode.RECENT

    private val adapter = DecksAdapter(
        onOpen = { deck -> openDeck(deck) },
        onEdit = { deck -> showEditDialog(deck) },
        onDelete = { deck -> confirmDelete(deck) },
        isPremiumUser = { isPremiumUser },
        onLocked = { deck ->
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deck.id)
            )
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserDecksBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.recycler.setHasFixedSize(true)

        // Actions
        b.fabAdd.setOnClickListener { showCreateDialog() }
        b.btnCreateFirst.setOnClickListener { showCreateDialog() }

        // Search
        b.etSearch.addTextChangedListenerCompat { applyFilterSortAndRender() }

        // Chips
        b.chipAll.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                filterMode = FilterMode.ALL
                applyFilterSortAndRender()
            }
        }
        b.chipFreeOnly.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                filterMode = FilterMode.FREE
                applyFilterSortAndRender()
            }
        }
        b.chipPremiumOnly.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                filterMode = FilterMode.PREMIUM
                applyFilterSortAndRender()
            }
        }

        // Sort
        b.btnSort.setOnClickListener { showSortDialog() }

        // Explore / AI (demo placeholders)
        b.btnExplorePublic.setOnClickListener {
            Snackbar.make(b.root, "Explore public decks: coming soon (demo).", Snackbar.LENGTH_SHORT).show()
        }
        b.btnAiGenerate.setOnClickListener {
            Snackbar.make(b.root, "AI generate: coming soon (demo).", Snackbar.LENGTH_SHORT).show()
        }

        reload()
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
            val premium = try {
                db.isUserPremium(me.id)
            } catch (_: Exception) {
                false
            }

            val decks = try {
                db.getDecksForOwner(me.id)
            } catch (_: Exception) {
                emptyList()
            }

            val cardCount = try {
                countCardsForOwner(me.id)
            } catch (_: Exception) {
                0
            }

            postUi {
                isPremiumUser = premium
                all = decks
                // Update header stats
                b.tvStats.text = "${decks.size} decks • $cardCount cards"
                applyFilterSortAndRender()
                inFlight.set(false)
            }
        }
    }

    private fun applyFilterSortAndRender() {
        val query = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var list = all

        // Filter
        list = when (filterMode) {
            FilterMode.ALL -> list
            FilterMode.FREE -> list.filter { !it.isPremium }
            FilterMode.PREMIUM -> list.filter { it.isPremium }
        }

        // Search
        if (query.isNotBlank()) {
            list = list.filter {
                it.title.lowercase().contains(query) ||
                        (it.description ?: "").lowercase().contains(query)
            }
        }

        // Sort
        list = when (sortMode) {
            SortMode.RECENT -> list.sortedByDescending { it.createdAt }
            SortMode.A_Z -> list.sortedBy { it.title.lowercase() }
            SortMode.PREMIUM_FIRST -> list.sortedWith(
                compareByDescending<StarDeckDbHelper.DeckRow> { it.isPremium }
                    .thenByDescending { it.createdAt }
            )
        }

        adapter.submit(list)

        val empty = list.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showSortDialog() {
        val options = arrayOf("Recent", "A–Z", "Premium first")
        val current = when (sortMode) {
            SortMode.RECENT -> 0
            SortMode.A_Z -> 1
            SortMode.PREMIUM_FIRST -> 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort decks")
            .setSingleChoiceItems(options, current) { dialog, which ->
                sortMode = when (which) {
                    1 -> SortMode.A_Z
                    2 -> SortMode.PREMIUM_FIRST
                    else -> SortMode.RECENT
                }
                b.btnSort.text = options[which]
                applyFilterSortAndRender()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDeck(deck: StarDeckDbHelper.DeckRow) {
        // Premium lock (demo)
        if (deck.isPremium && !isPremiumUser) {
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, deck.id)
            )
            return
        }

        startActivity(
            Intent(requireContext(), DeckCardsActivity::class.java)
                .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deck.id)
        )
    }

    private fun showCreateDialog() {
        val me = session.load() ?: return

        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Deck"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilTitle.error = null
                d.tilDescription.error = null

                val title = d.etTitle.text?.toString().orEmpty().trim()
                val desc = d.etDescription.text?.toString()?.trim()

                if (title.isBlank()) {
                    d.tilTitle.error = "Title is required"
                    return@setOnClickListener
                }
                if (title.length > 40) {
                    d.tilTitle.error = "Max 40 characters"
                    return@setOnClickListener
                }
                if (all.any { it.title.equals(title, ignoreCase = true) }) {
                    d.tilTitle.error = "Deck title already exists"
                    return@setOnClickListener
                }
                if (!desc.isNullOrBlank() && desc.length > 200) {
                    d.tilDescription.error = "Max 200 characters"
                    return@setOnClickListener
                }

                executor.execute {
                    val ok = try {
                        db.createDeck(me.id, title, desc)
                        true
                    } catch (_: Exception) {
                        false
                    }

                    postUi {
                        if (ok) {
                            Snackbar.make(b.root, "Deck created", Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                            reload()
                        } else {
                            Snackbar.make(b.root, "Could not create deck", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showEditDialog(deck: StarDeckDbHelper.DeckRow) {
        val me = session.load() ?: return

        // keep premium demo clean
        if (deck.isPremium) {
            Snackbar.make(b.root, "Premium decks can’t be edited (demo).", Snackbar.LENGTH_SHORT).show()
            return
        }

        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Deck"
        d.etTitle.setText(deck.title)
        d.etDescription.setText(deck.description.orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilTitle.error = null
                d.tilDescription.error = null

                val title = d.etTitle.text?.toString().orEmpty().trim()
                val desc = d.etDescription.text?.toString()?.trim()

                if (title.isBlank()) {
                    d.tilTitle.error = "Title is required"
                    return@setOnClickListener
                }
                if (title.length > 40) {
                    d.tilTitle.error = "Max 40 characters"
                    return@setOnClickListener
                }
                if (all.any { it.id != deck.id && it.title.equals(title, ignoreCase = true) }) {
                    d.tilTitle.error = "Deck title already exists"
                    return@setOnClickListener
                }
                if (!desc.isNullOrBlank() && desc.length > 200) {
                    d.tilDescription.error = "Max 200 characters"
                    return@setOnClickListener
                }

                executor.execute {
                    val rows = try {
                        db.updateDeck(me.id, deck.id, title, desc)
                    } catch (_: Exception) {
                        0
                    }

                    postUi {
                        if (rows == 1) {
                            Snackbar.make(b.root, "Saved", Snackbar.LENGTH_SHORT).show()
                            dialog.dismiss()
                            reload()
                        } else {
                            Snackbar.make(b.root, "Could not update", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun confirmDelete(deck: StarDeckDbHelper.DeckRow) {
        val me = session.load() ?: return

        if (deck.isPremium) {
            Snackbar.make(b.root, "Premium demo deck can’t be deleted.", Snackbar.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete deck?")
            .setMessage("“${deck.title}” and its cards will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                executor.execute {
                    val rows = try {
                        db.deleteDeck(me.id, deck.id)
                    } catch (_: Exception) {
                        0
                    }
                    postUi {
                        if (rows == 1) {
                            Snackbar.make(b.root, "Deck deleted", Snackbar.LENGTH_SHORT).show()
                            reload()
                        } else {
                            Snackbar.make(b.root, "Could not delete", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun countCardsForOwner(ownerUserId: Long): Int {
        // Single fast query (no loops over decks)
        val sql = """
            SELECT COUNT(c.${DbContract.C_ID})
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID} = ?
              AND d.${DbContract.D_STATUS} = '${DbContract.DECK_ACTIVE}'
        """.trimIndent()

        db.readableDatabase.rawQuery(sql, arrayOf(ownerUserId.toString())).use { cur ->
            return if (cur.moveToFirst()) cur.getInt(0) else 0
        }
    }

    private fun postUi(block: () -> Unit) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            if (!isAdded || _b == null) return@runOnUiThread
            block()
        }
    }

    private enum class FilterMode { ALL, FREE, PREMIUM }
    private enum class SortMode { RECENT, A_Z, PREMIUM_FIRST }

    /**
     * Tiny helper to avoid importing addTextChangedListener from KTX
     * (keeps fewer dependencies and avoids null issues).
     */
    private fun android.widget.EditText.addTextChangedListenerCompat(onChanged: () -> Unit) {
        this.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    // ---------- Adapter (FIXES your compile errors) ----------
    private class DecksAdapter(
        val onOpen: (StarDeckDbHelper.DeckRow) -> Unit,
        val onEdit: (StarDeckDbHelper.DeckRow) -> Unit,
        val onDelete: (StarDeckDbHelper.DeckRow) -> Unit,
        val isPremiumUser: () -> Boolean,
        val onLocked: (StarDeckDbHelper.DeckRow) -> Unit
    ) : RecyclerView.Adapter<DecksAdapter.VH>() {

        private val items = mutableListOf<StarDeckDbHelper.DeckRow>()

        fun submit(newItems: List<StarDeckDbHelper.DeckRow>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemDeckBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b, onOpen, onEdit, onDelete, isPremiumUser, onLocked)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        class VH(
            private val b: ItemDeckBinding,
            private val onOpen: (StarDeckDbHelper.DeckRow) -> Unit,
            private val onEdit: (StarDeckDbHelper.DeckRow) -> Unit,
            private val onDelete: (StarDeckDbHelper.DeckRow) -> Unit,
            private val isPremiumUser: () -> Boolean,
            private val onLocked: (StarDeckDbHelper.DeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(deck: StarDeckDbHelper.DeckRow) {
                b.tvTitle.text = deck.title
                b.tvDesc.text = deck.description?.takeIf { it.isNotBlank() } ?: "No description"

                val premiumUser = isPremiumUser()

                if (deck.isPremium) {
                    b.chipPremium.visibility = View.VISIBLE
                    b.chipPremium.text = if (premiumUser) "⭐ Premium" else "🔒 Premium"

                    // lock edit/delete on premium demo deck
                    b.btnEdit.visibility = View.GONE
                    b.btnDelete.visibility = View.GONE

                    b.root.setOnClickListener {
                        if (premiumUser) onOpen(deck) else onLocked(deck)
                    }
                } else {
                    b.chipPremium.visibility = View.GONE

                    b.btnEdit.visibility = View.VISIBLE
                    b.btnDelete.visibility = View.VISIBLE

                    b.root.setOnClickListener { onOpen(deck) }
                    b.btnEdit.setOnClickListener { onEdit(deck) }
                    b.btnDelete.setOnClickListener { onDelete(deck) }
                }
            }
        }
    }
}