package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.DialogEditDeckBinding
import com.example.stardeckapplication.databinding.FragmentUserDecksBinding
import com.example.stardeckapplication.databinding.ItemDeckBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.CategoryDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.LanguageDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.SubjectDao
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.db.UserDeckDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class UserDecksFragment : Fragment(R.layout.fragment_user_decks) {

    private var _b: FragmentUserDecksBinding? = null
    private val b get() = _b!!

    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val deckDao by lazy { UserDeckDao(dbHelper) }
    private val userDao by lazy { UserDao(dbHelper) }
    private val cardDao by lazy { CardDao(dbHelper) }
    private val categoryDao by lazy { CategoryDao(dbHelper) }
    private val subjectDao by lazy { SubjectDao(dbHelper) }
    private val languageDao by lazy { LanguageDao(dbHelper) }
    private val session by lazy { SessionManager(requireContext()) }
    private val achievementSync by lazy { AchievementSyncHelper(dbHelper) }
    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)

    private var isPremiumUser = false
    private var all: List<UserDeckDao.DeckRow> = emptyList()
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

        b.fabAdd.setOnClickListener { showCreateDialog() }
        b.btnCreateFirst.setOnClickListener { showCreateDialog() }

        b.etSearch.addTextChangedListenerCompat { applyFilterSortAndRender() }

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
            val premium = runCatching { userDao.isUserPremium(me.id) }.getOrDefault(false)
            val decks = runCatching { deckDao.getDecksForOwner(me.id) }.getOrDefault(emptyList())
            val cardCount = runCatching { cardDao.getTotalCardCountForOwnerAllStatuses(me.id) }.getOrDefault(0)

            postUi {
                isPremiumUser = premium
                all = decks
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
            FilterMode.ALL -> list
            FilterMode.FREE -> list.filter { !it.isPremium }
            FilterMode.PREMIUM -> list.filter { it.isPremium }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.title.lowercase().contains(query) ||
                        (it.description ?: "").lowercase().contains(query) ||
                        (it.categoryName ?: "").lowercase().contains(query) ||
                        (it.subjectName ?: "").lowercase().contains(query) ||
                        (it.languageName ?: "").lowercase().contains(query)
            }
        }

        list = when (sortMode) {
            SortMode.RECENT -> list.sortedByDescending { it.createdAt }
            SortMode.A_Z -> list.sortedBy { it.title.lowercase() }
            SortMode.PREMIUM_FIRST -> list.sortedWith(
                compareByDescending<UserDeckDao.DeckRow> { it.isPremium }
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
        val d = DialogEditDeckBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Deck"
        d.swPublic.isChecked = false
        d.groupCategory.visibility = View.VISIBLE
        d.groupSubject.visibility = View.VISIBLE
        d.groupLanguage.visibility = View.VISIBLE
        updateVisibilityNote(d)

        val categories = bindCategoryDropdown(d, selectedCategoryId = null)
        var subjectOptions = bindSubjectDropdown(
            d = d,
            categoryId = null,
            selectedSubjectId = null
        )
        val languageOptions = bindLanguageDropdown(
            d = d,
            selectedLanguageId = null
        )

        d.actCategory.setOnItemClickListener { _, _, _, _ ->
            val categoryId = resolveSelectedCategoryId(
                rawText = d.actCategory.text?.toString().orEmpty(),
                options = categories
            )
            subjectOptions = bindSubjectDropdown(
                d = d,
                categoryId = categoryId,
                selectedSubjectId = null
            )
        }

        d.swPublic.setOnCheckedChangeListener { _, _ -> updateVisibilityNote(d) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error = null
                    d.tilDescription.error = null
                    d.tilCategory.error = null
                    d.tilSubject.error = null
                    d.tilLanguage.error = null

                    val title = d.etTitle.text?.toString().orEmpty().trim()
                    val desc = d.etDescription.text?.toString()?.trim()
                    val isPublic = d.swPublic.isChecked
                    val categoryId = resolveSelectedCategoryId(
                        rawText = d.actCategory.text?.toString().orEmpty(),
                        options = categories
                    )
                    val subjectId = resolveSelectedSubjectId(
                        rawText = d.actSubject.text?.toString().orEmpty(),
                        options = subjectOptions
                    )
                    val languageId = resolveSelectedLanguageId(
                        rawText = d.actLanguage.text?.toString().orEmpty(),
                        options = languageOptions
                    )

                    when {
                        title.isBlank() -> {
                            d.tilTitle.error = "Title is required"
                            return@setOnClickListener
                        }

                        title.length > 40 -> {
                            d.tilTitle.error = "Max 40 characters"
                            return@setOnClickListener
                        }

                        all.any { it.title.equals(title, ignoreCase = true) } -> {
                            d.tilTitle.error = "Deck title already exists"
                            return@setOnClickListener
                        }

                        !desc.isNullOrBlank() && desc.length > 200 -> {
                            d.tilDescription.error = "Max 200 characters"
                            return@setOnClickListener
                        }

                        categoryId == INVALID_CATEGORY_ID -> {
                            d.tilCategory.error = "Please choose a valid category"
                            return@setOnClickListener
                        }

                        subjectId == INVALID_SUBJECT_ID -> {
                            d.tilSubject.error = "Please choose a valid subject"
                            return@setOnClickListener
                        }

                        languageId == INVALID_LANGUAGE_ID -> {
                            d.tilLanguage.error = "Please choose a valid language"
                            return@setOnClickListener
                        }
                    }

                    executor.execute {
                        val deckId = runCatching {
                            deckDao.createDeck(
                                ownerUserId = me.id,
                                title = title,
                                description = desc,
                                isPremium = false,
                                isPublic = isPublic,
                                categoryId = categoryId,
                                subjectId = subjectId,
                                languageId = languageId
                            )
                        }.getOrDefault(-1L)

                        postUi {
                            if (deckId > 0L) {
                                val unlocked = achievementSync.syncForUser(me.id)
                                val message = if (unlocked > 0) {
                                    "Deck created • $unlocked achievement(s) unlocked!"
                                } else {
                                    "Deck created"
                                }
                                Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
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
        d.tvTitle.text = "Edit Deck"
        d.etTitle.setText(deck.title)
        d.etDescription.setText(deck.description.orEmpty())
        d.swPublic.isChecked = deck.isPublic
        d.groupCategory.visibility = View.VISIBLE
        d.groupSubject.visibility = View.VISIBLE
        d.groupLanguage.visibility = View.VISIBLE
        updateVisibilityNote(d)

        val categories = bindCategoryDropdown(d, selectedCategoryId = deck.categoryId)
        var subjectOptions = bindSubjectDropdown(
            d = d,
            categoryId = deck.categoryId,
            selectedSubjectId = deck.subjectId
        )
        val languageOptions = bindLanguageDropdown(
            d = d,
            selectedLanguageId = deck.languageId
        )

        d.actCategory.setOnItemClickListener { _, _, _, _ ->
            val categoryId = resolveSelectedCategoryId(
                rawText = d.actCategory.text?.toString().orEmpty(),
                options = categories
            )
            subjectOptions = bindSubjectDropdown(
                d = d,
                categoryId = if (categoryId == INVALID_CATEGORY_ID) null else categoryId,
                selectedSubjectId = null
            )
        }

        d.swPublic.setOnCheckedChangeListener { _, _ -> updateVisibilityNote(d) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    d.tilTitle.error = null
                    d.tilDescription.error = null
                    d.tilCategory.error = null
                    d.tilSubject.error = null
                    d.tilLanguage.error = null

                    val title = d.etTitle.text?.toString().orEmpty().trim()
                    val desc = d.etDescription.text?.toString()?.trim()
                    val isPublic = d.swPublic.isChecked
                    val categoryId = resolveSelectedCategoryId(
                        rawText = d.actCategory.text?.toString().orEmpty(),
                        options = categories
                    )
                    val subjectId = resolveSelectedSubjectId(
                        rawText = d.actSubject.text?.toString().orEmpty(),
                        options = subjectOptions
                    )
                    val languageId = resolveSelectedLanguageId(
                        rawText = d.actLanguage.text?.toString().orEmpty(),
                        options = languageOptions
                    )

                    when {
                        title.isBlank() -> {
                            d.tilTitle.error = "Title is required"
                            return@setOnClickListener
                        }

                        title.length > 40 -> {
                            d.tilTitle.error = "Max 40 characters"
                            return@setOnClickListener
                        }

                        all.any { it.id != deck.id && it.title.equals(title, ignoreCase = true) } -> {
                            d.tilTitle.error = "Deck title already exists"
                            return@setOnClickListener
                        }

                        !desc.isNullOrBlank() && desc.length > 200 -> {
                            d.tilDescription.error = "Max 200 characters"
                            return@setOnClickListener
                        }

                        categoryId == INVALID_CATEGORY_ID -> {
                            d.tilCategory.error = "Please choose a valid category"
                            return@setOnClickListener
                        }

                        subjectId == INVALID_SUBJECT_ID -> {
                            d.tilSubject.error = "Please choose a valid subject"
                            return@setOnClickListener
                        }

                        languageId == INVALID_LANGUAGE_ID -> {
                            d.tilLanguage.error = "Please choose a valid language"
                            return@setOnClickListener
                        }
                    }

                    executor.execute {
                        val rows = runCatching {
                            deckDao.updateDeck(
                                deckId = deck.id,
                                title = title,
                                description = desc,
                                isPublic = isPublic,
                                categoryId = categoryId,
                                subjectId = subjectId,
                                languageId = languageId
                            )
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

    private fun bindCategoryDropdown(
        d: DialogEditDeckBinding,
        selectedCategoryId: Long?
    ): List<CategoryDao.SelectableCategory> {
        val options = categoryDao.getSelectableCategories(selectedCategoryId)
        val labels = mutableListOf(NO_CATEGORY_LABEL).apply {
            addAll(options.map { it.name })
        }

        d.actCategory.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        d.actCategory.setOnClickListener { d.actCategory.showDropDown() }

        val selectedLabel = options.firstOrNull { it.id == selectedCategoryId }?.name ?: NO_CATEGORY_LABEL
        d.actCategory.setText(selectedLabel, false)

        d.tvCategoryNote.text =
            if (options.isEmpty()) "No active categories yet. Admin can create them from Master Data."
            else "Optional. Categories help organize decks."

        return options
    }

    private fun bindSubjectDropdown(
        d: DialogEditDeckBinding,
        categoryId: Long?,
        selectedSubjectId: Long?
    ): List<SubjectDao.SelectableSubject> {
        if (categoryId == null || categoryId <= 0L) {
            d.actSubject.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf(NO_SUBJECT_LABEL))
            )
            d.actSubject.setText(NO_SUBJECT_LABEL, false)
            d.actSubject.isEnabled = false
            d.tilSubject.isEnabled = false
            d.tvSubjectNote.text = "Choose a category first to select subjects."
            return emptyList()
        }

        val options = subjectDao.getSelectableSubjects(categoryId, selectedSubjectId)
        val labels = mutableListOf(NO_SUBJECT_LABEL).apply {
            addAll(options.map { it.name })
        }

        d.actSubject.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        d.actSubject.setOnClickListener { d.actSubject.showDropDown() }
        d.actSubject.isEnabled = true
        d.tilSubject.isEnabled = true

        val selectedLabel = options.firstOrNull { it.id == selectedSubjectId }?.name ?: NO_SUBJECT_LABEL
        d.actSubject.setText(selectedLabel, false)

        d.tvSubjectNote.text =
            if (options.isEmpty()) "No active subjects in this category yet."
            else "Optional. Subjects provide more specific organization."

        return options
    }

    private fun bindLanguageDropdown(
        d: DialogEditDeckBinding,
        selectedLanguageId: Long?
    ): List<LanguageDao.SelectableLanguage> {
        val options = languageDao.getSelectableLanguages(selectedLanguageId)
        val labels = mutableListOf(NO_LANGUAGE_LABEL).apply {
            addAll(options.map { it.name })
        }

        d.actLanguage.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        d.actLanguage.setOnClickListener { d.actLanguage.showDropDown() }

        val selectedLabel = options.firstOrNull { it.id == selectedLanguageId }?.name ?: NO_LANGUAGE_LABEL
        d.actLanguage.setText(selectedLabel, false)

        d.tvLanguageNote.text =
            if (options.isEmpty()) "No active languages yet. Admin can create them from Master Data."
            else "Optional. Language helps classify deck content."

        return options
    }

    private fun resolveSelectedCategoryId(
        rawText: String,
        options: List<CategoryDao.SelectableCategory>
    ): Long? {
        val clean = rawText.trim()
        if (clean.isBlank() || clean == NO_CATEGORY_LABEL) return null

        val matched = options.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        return matched?.id ?: INVALID_CATEGORY_ID
    }

    private fun resolveSelectedSubjectId(
        rawText: String,
        options: List<SubjectDao.SelectableSubject>
    ): Long? {
        val clean = rawText.trim()
        if (clean.isBlank() || clean == NO_SUBJECT_LABEL) return null

        val matched = options.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        return matched?.id ?: INVALID_SUBJECT_ID
    }

    private fun resolveSelectedLanguageId(
        rawText: String,
        options: List<LanguageDao.SelectableLanguage>
    ): Long? {
        val clean = rawText.trim()
        if (clean.isBlank() || clean == NO_LANGUAGE_LABEL) return null

        val matched = options.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        return matched?.id ?: INVALID_LANGUAGE_ID
    }

    private fun updateVisibilityNote(d: DialogEditDeckBinding) {
        d.tvVisibilityNote.text = if (d.swPublic.isChecked) {
            "Public decks can be shown later in Explore."
        } else {
            "Private decks are only visible to your account."
        }
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
                    val rows = runCatching { deckDao.deleteDeck(deck.id) }.getOrDefault(0)
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
    private enum class SortMode { RECENT, A_Z, PREMIUM_FIRST }

    private class DecksAdapter(
        private val onOpen: (UserDeckDao.DeckRow) -> Unit,
        private val onEdit: (UserDeckDao.DeckRow) -> Unit,
        private val onDelete: (UserDeckDao.DeckRow) -> Unit,
        private val isPremiumUser: () -> Boolean,
        private val onLocked: (UserDeckDao.DeckRow) -> Unit
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
            private val b: ItemDeckBinding,
            private val onOpen: (UserDeckDao.DeckRow) -> Unit,
            private val onEdit: (UserDeckDao.DeckRow) -> Unit,
            private val onDelete: (UserDeckDao.DeckRow) -> Unit,
            private val isPremiumUser: () -> Boolean,
            private val onLocked: (UserDeckDao.DeckRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(deck: UserDeckDao.DeckRow) {
                b.tvTitle.text = deck.title
                b.tvDesc.text = deck.description?.takeIf { it.isNotBlank() } ?: "No description"
                b.chipVisibility.text = if (deck.isPublic) "Public" else "Private"

                if (deck.categoryName.isNullOrBlank()) {
                    b.chipCategory.visibility = View.GONE
                } else {
                    b.chipCategory.visibility = View.VISIBLE
                    b.chipCategory.text = deck.categoryName
                }

                if (deck.subjectName.isNullOrBlank()) {
                    b.chipSubject.visibility = View.GONE
                } else {
                    b.chipSubject.visibility = View.VISIBLE
                    b.chipSubject.text = deck.subjectName
                }

                if (deck.languageName.isNullOrBlank()) {
                    b.chipLanguage.visibility = View.GONE
                } else {
                    b.chipLanguage.visibility = View.VISIBLE
                    b.chipLanguage.text = deck.languageName
                }

                val premiumUser = isPremiumUser()

                if (deck.isPremium) {
                    b.chipPremium.visibility = View.VISIBLE
                    b.chipPremium.text = if (premiumUser) "Premium" else "Locked"
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

    private companion object {
        private const val NO_CATEGORY_LABEL = "No category"
        private const val NO_SUBJECT_LABEL = "No subject"
        private const val NO_LANGUAGE_LABEL = "No language"
        private const val INVALID_CATEGORY_ID = -9999L
        private const val INVALID_SUBJECT_ID = -9998L
        private const val INVALID_LANGUAGE_ID = -9997L
    }
}

private fun EditText.addTextChangedListenerCompat(onChanged: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) { onChanged() }
    })
}