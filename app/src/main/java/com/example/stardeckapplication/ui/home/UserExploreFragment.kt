package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserExploreBinding
import com.example.stardeckapplication.databinding.ItemPublicDeckBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.ExploreDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class UserExploreFragment : Fragment(R.layout.fragment_user_explore) {

    private var _b: FragmentUserExploreBinding? = null
    private val b get() = _b!!

    private val session    by lazy { SessionManager(requireContext()) }
    private val dbHelper   by lazy { StarDeckDbHelper(requireContext()) }
    private val exploreDao by lazy { ExploreDao(dbHelper) }

    private var allDecks     : List<ExploreDao.PublicDeckCatalogRow> = emptyList()
    private var filterFree    = false
    private var filterPremium = false
    private var searchQuery   = ""

    private val adapter = PublicDeckAdapter(
        onCopy    = { copyDeck(it) },
        onPreview = { previewDeck(it) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserExploreBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            requireActivity().finish()
            return
        }

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.etSearch.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            applyFilters()
        }

        b.chipFree.setOnClickListener {
            filterFree = !filterFree
            b.chipFree.isChecked = filterFree
            applyFilters()
        }

        b.chipPremium.setOnClickListener {
            filterPremium = !filterPremium
            b.chipPremium.isChecked = filterPremium
            applyFilters()
        }

        loadDecks()
    }

    override fun onResume() {
        super.onResume()
        loadDecks()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun loadDecks() {
        val me = session.load() ?: return
        allDecks = runCatching {
            exploreDao.getPublicDeckCatalogForUser(me.id)
        }.getOrElse {
            Snackbar.make(b.root, "Could not load decks.", Snackbar.LENGTH_LONG).show()
            emptyList()
        }
        applyFilters()
    }

    private fun applyFilters() {
        val q = searchQuery.trim().lowercase()
        var list: List<ExploreDao.PublicDeckCatalogRow> = allDecks

        if (filterFree    && !filterPremium) list = list.filter { !it.isPremium }
        if (filterPremium && !filterFree)    list = list.filter {  it.isPremium }

        if (q.isNotBlank()) {
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.ownerName.lowercase().contains(q) ||
                        (it.description ?: "").lowercase().contains(q)
            }
        }

        adapter.submitList(list)
        b.tvCount.text = "${list.size} deck(s) found"

        val isEmpty = list.isEmpty()
        b.groupEmpty.visibility = if (isEmpty) View.VISIBLE  else View.GONE
        b.recycler.visibility   = if (isEmpty) View.GONE     else View.VISIBLE
    }

    private fun copyDeck(row: ExploreDao.PublicDeckCatalogRow) {
        val me = session.load() ?: return

        if (row.isLocked) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Premium deck")
                .setMessage("Upgrade to Premium to copy this deck to your library.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Copy deck?")
            .setMessage("\"${row.title}\" by ${row.ownerName} will be added to your library.")
            .setPositiveButton("Copy") { _, _ ->
                val newId = runCatching {
                    exploreDao.copyPublicDeckToUser(
                        sourceDeckId  = row.deckId,
                        targetUserId  = me.id
                    )
                }.getOrDefault(-1L)

                when {
                    newId > 0L -> Snackbar.make(
                        b.root, "✅ Deck copied to your library!", Snackbar.LENGTH_LONG
                    ).show()
                    else -> Snackbar.make(
                        b.root, "Could not copy deck. Try again.", Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun previewDeck(row: ExploreDao.PublicDeckCatalogRow) {
        if (row.isLocked) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🔒 Premium: ${row.title}")
                .setMessage("This is a premium deck.\nUpgrade to preview all ${row.cardCount} cards.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val cards = runCatching {
            exploreDao.getPublicDeckCardsForUser(row.deckId)
        }.getOrElse { emptyList() }

        if (cards.isEmpty()) {
            Snackbar.make(b.root, "No cards to preview.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val preview = cards.take(5)
            .joinToString("\n\n") { "Q: ${it.question}\nA: ${it.answer}" }
        val suffix  = if (cards.size > 5) "\n\n...and ${cards.size - 5} more cards." else ""

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Preview: ${row.title}")
            .setMessage(preview + suffix)
            .setPositiveButton("Copy to Library") { _, _ -> copyDeck(row) }
            .setNegativeButton("Close", null)
            .show()
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class PublicDeckAdapter(
        private val onCopy    : (ExploreDao.PublicDeckCatalogRow) -> Unit,
        private val onPreview : (ExploreDao.PublicDeckCatalogRow) -> Unit
    ) : ListAdapter<ExploreDao.PublicDeckCatalogRow, PublicDeckAdapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemPublicDeckBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding, onCopy, onPreview)
        }

        override fun onBindViewHolder(holder: VH, position: Int) =
            holder.bind(getItem(position))

        class VH(
            private val b         : ItemPublicDeckBinding,
            private val onCopy    : (ExploreDao.PublicDeckCatalogRow) -> Unit,
            private val onPreview : (ExploreDao.PublicDeckCatalogRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: ExploreDao.PublicDeckCatalogRow) {
                b.tvTitle.text     = if (row.isPremium) "⭐ ${row.title}" else row.title
                b.tvOwner.text     = "by ${row.ownerName}"
                b.tvDesc.text      = row.description ?: "No description."
                b.tvCardCount.text = "${row.cardCount} cards"

                if (row.isLocked) {
                    b.tvLocked.visibility = View.VISIBLE
                    b.tvLocked.text       = "🔒 Premium"
                    b.btnCopy.text        = "Locked"
                } else {
                    b.tvLocked.visibility = View.GONE
                    b.btnCopy.text        = "Copy"
                }

                b.btnCopy.setOnClickListener    { onCopy(row) }
                b.btnPreview.setOnClickListener { onPreview(row) }
                b.root.setOnClickListener       { onPreview(row) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<ExploreDao.PublicDeckCatalogRow>() {
            override fun areItemsTheSame(
                oldItem: ExploreDao.PublicDeckCatalogRow,
                newItem: ExploreDao.PublicDeckCatalogRow
            ) = oldItem.deckId == newItem.deckId

            override fun areContentsTheSame(
                oldItem: ExploreDao.PublicDeckCatalogRow,
                newItem: ExploreDao.PublicDeckCatalogRow
            ) = oldItem == newItem
        }
    }
}