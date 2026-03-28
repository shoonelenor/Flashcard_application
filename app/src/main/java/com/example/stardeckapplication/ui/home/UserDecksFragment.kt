package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.DialogEditDeckBinding
import com.example.stardeckapplication.databinding.FragmentUserDecksBinding
import com.example.stardeckapplication.databinding.ItemDeckBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.db.UserDeckDao
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

    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val deckDao  by lazy { UserDeckDao(dbHelper) }
    private val userDao  by lazy { UserDao(dbHelper) }     // ✅ isUserPremium correct home
    private val cardDao  by lazy { CardDao(dbHelper) }     // ✅ getTotalCardCountForOwnerAllStatuses
    private val session  by lazy { SessionManager(requireContext()) }
    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    private var isPremiumUser = false
    private var all           : List<UserDeckDao.DeckRow> = emptyList()
    private var filterMode    : FilterMode = FilterMode.ALL
    private var sortMode      : SortMode   = SortMode.RECENT

    private val adapter = DecksAdapter(
        onOpen        = { deck -> openDeck(deck) },
        onEdit        = { deck -> showEditDialog(deck) },
        onDelete      = { deck -> confirmDelete(deck) },
        isPremiumUser = { isPremiumUser },
        onLocked      = { deck ->
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
        b.recycler.adapter       = adapter
        b.recycler.setHasFixedSize(true)

        b.fabAdd.setOnClickListener         { showCreateDialog() }
        b.btnCreateFirst.setOnClickListener { showCreateDialog() }

        b.etSearch.addTextChangedListenerCompat { applyFilterSortAndRender() }

        b.chipAll.setOnCheckedChangeListener { _, checked ->
            if (checked) { filterMode = FilterMode.ALL; applyFilterSortAndRender() }
        }
        b.chipFreeOnly.setOnCheckedChangeListener { _, checked ->
            if (checked) { filterMode = FilterMode.FREE; applyFilterSortAndRender() }
        }
        b.chipPremiumOnly.setOnCheckedChangeListener { _, checked ->
            if (checked) { filterMode = FilterMode.PREMIUM; applyFilterSortAndRender() }
        }

        b.btnSort.setOnClickListener { showSortDialog() }

        b.btnExplorePublic.setOnClickListener {
            (activity as? UserHomeActivity)?.openTab(R.id.nav_explore)
        }

        b.btnAiGenerate.setOnClickListener {
            Snackbar.make(
                b.root,
                "AI generate framework is ready. Real logic will be added later.",
                Snackbar.LENGTH_SHORT
            ).show()
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
            // ✅ UserDao.isUserPremium — correct home
            val premium   : Boolean              = runCatching { userDao.isUserPremium(me.id) }.getOrDefault(false)
            val decks     : List<UserDeckDao.DeckRow> = runCatching { deckDao.getDecksForOwner(me.id) }.getOrDefault(emptyList())
            // ✅ CardDao.getTotalCardCountForOwnerAllStatuses — no more inline SQL in fragment
            val cardCount : Int                  = runCatching { cardDao.getTotalCardCountForOwnerAllStatuses(me.id) }.getOrDefault(0)

            postUi {
                isPremiumUser = premium
                all           = decks
                b.tvStats.text = "${decks.size} decks • $cardCount cards"
                applyFilterSortAndRender()
                inFlight.set(false)
            }
        }
    }

    private fun applyFilterSortAndRender() {
        if (_b == null) return

        val query = b.etSearch.text?.toString().orEmpty().trim().lowercase()

        var list: List<UserDeckDao.DeckRow> = all

        list = when (filterMode) {
            FilterMode.ALL     -> list
            FilterMode.FREE    -> list.filter { !it.isPremium }
            FilterMode.PREMIUM -> list.filter {  it.isPremium }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.title.lowercase().contains(query) ||
                        (it.description ?: "").lowercase().contains(query)
            }
        }

        list = when (sortMode) {
            SortMode.RECENT        -> list.sortedByDescending { it.createdAt }
            SortMode.A_Z           -> list.sortedBy { it.title.lowercase() }
            SortMode.PREMIUM_FIRST -> list.sortedWith(
                compareByDescending<UserDeckDao.DeckRow> { it.isPremium }
                    .thenByDescending { it.createdAt }
            )
        }

        adapter.submit(list)

        val empty = list.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility   = if (empty) View.GONE    else View.VISIBLE
    }

    private fun showSortDialog() {
        val options = arrayOf("Recent", "A–Z", "Premium first")
        val current = when (sortMode) {
            SortMode.RECENT        -> 0
            SortMode.A_Z           -> 1
            SortMode.PREMIUM_FIRST -> 2
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort decks")
            .setSingleChoiceItems(options, current) { dialog, which ->
                sortMode = when (which) {
                    1    -> SortMode.A_Z
                    2    -> SortMode.PREMIUM_FIRST
                    else -> SortMode.RECENT
                }
                b.btnSort.text = options[which]
                applyFilterSortAndRender()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            Intent(requireContext(), DeckCardsActivity::class.java)
                .putExtra(DeckCardsActivity.EXTRA_DECK_ID, deck.id)
        )
    }

    private fun showCreateDialog() {
        val me = session.load() ?: return
        val d  = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text       = "Create Deck"
        d.swPublic.isChecked = false
        updateVisibilityNote(d)
        d.swPublic.setOnCheckedChangeListener { _, _ -> updateVisibilityNote(d) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error       = null
                    d.tilDescription.error = null

                    val title    = d.etTitle.text?.toString().orEmpty().trim()
                    val desc     = d.etDescription.text?.toString()?.trim()
                    val isPublic = d.swPublic.isChecked

                    when {
                        title.isBlank()  -> { d.tilTitle.error = "Title is required";    return@setOnClickListener }
                        title.length > 40 -> { d.tilTitle.error = "Max 40 characters";    return@setOnClickListener }
                        all.any { it.title.equals(title, ignoreCase = true) } -> {
                            d.tilTitle.error = "Deck title already exists"; return@setOnClickListener
                        }
                        !desc.isNullOrBlank() && desc.length > 200 -> {
                            d.tilDescription.error = "Max 200 characters"; return@setOnClickListener
                        }
                    }

                    executor.execute {
                        val ok: Boolean = runCatching {
                            deckDao.createDeck(
                                ownerUserId = me.id,
                                title       = title,
                                description = desc,
                                isPremium   = false,
                                isPublic    = isPublic
                            )
                            true
                        }.getOrDefault(false)

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

    private fun showEditDialog(deck: UserDeckDao.DeckRow) {
        if (deck.isPremium) {
            Snackbar.make(b.root, "Premium demo deck can't be edited.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text       = "Edit Deck"
        d.etTitle.setText(deck.title)
        d.etDescription.setText(deck.description.orEmpty())
        d.swPublic.isChecked = deck.isPublic
        updateVisibilityNote(d)
        d.swPublic.setOnCheckedChangeListener { _, _ -> updateVisibilityNote(d) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error       = null
                    d.tilDescription.error = null

                    val title    = d.etTitle.text?.toString().orEmpty().trim()
                    val desc     = d.etDescription.text?.toString()?.trim()
                    val isPublic = d.swPublic.isChecked

                    when {
                        title.isBlank()   -> { d.tilTitle.error = "Title is required";    return@setOnClickListener }
                        title.length > 40 -> { d.tilTitle.error = "Max 40 characters";    return@setOnClickListener }
                        all.any { it.id != deck.id && it.title.equals(title, ignoreCase = true) } -> {
                            d.tilTitle.error = "Deck title already exists"; return@setOnClickListener
                        }
                        !desc.isNullOrBlank() && desc.length > 200 -> {
                            d.tilDescription.error = "Max 200 characters"; return@setOnClickListener
                        }
                    }

                    executor.execute {
                        val rows: Int = runCatching {
                            deckDao.updateDeck(deck.id, title, desc, isPublic)
                        }.getOrDefault(0)

                        postUi {
                            if (rows == 1) {
                                Snackbar.make(b.root, "Deck updated", Snackbar.LENGTH_SHORT).show()
                                dialog.dismiss()
                                reload()
                            } else {
                                Snackbar.make(b.root, "Could not update deck", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
        }
        dialog.show()
    }

    private fun updateVisibilityNote(d: DialogEditDeckBinding) {
        d.tvVisibilityNote.text = if (d.swPublic.isChecked)
            "Public decks can be shown later in Explore."
        else
            "Private decks are only visible to your account."
    }

    private fun confirmDelete(deck: UserDeckDao.DeckRow) {
        if (deck.isPremium) {
            Snackbar.make(b.root, "Premium demo deck can't be deleted.", Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete deck?")
            .setMessage("\"${deck.title}\" and its cards will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                executor.execute {
                    val rows: Int = runCatching { deckDao.deleteDeck(deck.id) }.getOrDefault(0)
                    postUi {
                        if (rows == 1) {
                            Snackbar.make(b.root, "Deck deleted", Snackbar.LENGTH_SHORT).show()
                            reload()
                        } else {
                            Snackbar.make(b.root, "Could not delete deck", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun postUi(block: () -> Unit) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            if (!isAdded || _b == null) return@runOnUiThread
            block()
        }
    }

    private enum class FilterMode { ALL, FREE, PREMIUM }
    private enum class SortMode   { RECENT, A_Z, PREMIUM_FIRST }

    private class DecksAdapter(
        private val onOpen        : (UserDeckDao.DeckRow) -> Unit,
        private val onEdit        : (UserDeckDao.DeckRow) -> Unit,
        private val onDelete      : (UserDeckDao.DeckRow) -> Unit,
        private val isPremiumUser : () -> Boolean,
        private val onLocked      : (UserDeckDao.DeckRow) -> Unit
    ) : RecyclerView.Adapter<DecksAdapter.DeckVH>() {

        private val items = mutableListOf<UserDeckDao.DeckRow>()

        fun submit(list: List<UserDeckDao.DeckRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckVH {
            val b = ItemDeckBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return DeckVH(b, onOpen, onEdit, onDelete, isPremiumUser, onLocked)
        }

        override fun onBindViewHolder(holder: DeckVH, position: Int) = holder.bind(items[position])

        class DeckVH(
            private val b             : ItemDeckBinding,
            private val onOpen        : (UserDeckDao.DeckRow) -> Unit,
            private val onEdit        : (UserDeckDao.DeckRow) -> Unit,
            private val onDelete      : (UserDeckDao.DeckRow) -> Unit,
            private val isPremiumUser : () -> Boolean,
            private val onLocked      : (UserDeckDao.DeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(deck: UserDeckDao.DeckRow) {
                b.tvTitle.text        = deck.title
                b.tvDesc.text         = deck.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.chipVisibility.text = if (deck.isPublic) "Public" else "Private"

                val premiumUser = isPremiumUser()

                if (deck.isPremium) {
                    b.chipPremium.visibility = View.VISIBLE
                    b.chipPremium.text       = if (premiumUser) "Premium" else "Locked"
                    b.btnEdit.visibility     = View.GONE
                    b.btnDelete.visibility   = View.GONE
                    b.root.setOnClickListener {
                        if (premiumUser) onOpen(deck) else onLocked(deck)
                    }
                } else {
                    b.chipPremium.visibility = View.GONE
                    b.btnEdit.visibility     = View.VISIBLE
                    b.btnDelete.visibility   = View.VISIBLE
                    b.root.setOnClickListener      { onOpen(deck) }
                    b.btnEdit.setOnClickListener   { onEdit(deck) }
                    b.btnDelete.setOnClickListener { onDelete(deck) }
                }
            }
        }
    }
}

private fun EditText.addTextChangedListenerCompat(onChanged: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)    = Unit
        override fun afterTextChanged(s: Editable?) { onChanged() }
    })
}