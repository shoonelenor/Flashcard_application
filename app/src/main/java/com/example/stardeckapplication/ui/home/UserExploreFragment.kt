package com.example.stardeckapplication.ui.home

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserExploreBinding
import com.example.stardeckapplication.databinding.ItemPublicDeckBinding
import com.example.stardeckapplication.db.CategoryDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.ExploreDao
import com.example.stardeckapplication.db.LanguageDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.SubjectDao
import com.example.stardeckapplication.ui.cards.DeckCardsActivity
import com.example.stardeckapplication.ui.profile.PremiumDemoActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class UserExploreFragment : Fragment(R.layout.fragment_user_explore) {

    private var _b: FragmentUserExploreBinding? = null
    private val b get() = _b!!

    private val session by lazy { SessionManager(requireContext()) }
    private val dbHelper by lazy { StarDeckDbHelper(requireContext()) }
    private val exploreDao by lazy { ExploreDao(dbHelper) }
    private val categoryDao by lazy { CategoryDao(dbHelper) }
    private val subjectDao by lazy { SubjectDao(dbHelper) }
    private val languageDao by lazy { LanguageDao(dbHelper) }

    private var allDecks: List<ExploreDao.PublicDeckCatalogRow> = emptyList()

    private var filterFree = false
    private var filterPremium = false
    private var searchQuery = ""

    private var selectedCategoryId: Long? = null
    private var selectedSubjectId: Long? = null
    private var selectedLanguageId: Long? = null

    private var categoryOptions: List<FilterOption> = emptyList()
    private var subjectOptions: List<FilterOption> = emptyList()
    private var languageOptions: List<FilterOption> = emptyList()

    private val adapter = PublicDeckAdapter(
        onOpen = { openDeck(it) },
        onCopy = { copyDeck(it) },
        onPreview = { previewDeck(it) },
        onReport = { reportDeck(it) }
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

        b.actCategory.setOnClickListener { b.actCategory.showDropDown() }
        b.actSubject.setOnClickListener { if (b.actSubject.isEnabled) b.actSubject.showDropDown() }
        b.actLanguage.setOnClickListener { b.actLanguage.showDropDown() }

        b.actCategory.setOnItemClickListener { _, _, _, _ ->
            selectedCategoryId = resolveOptionId(
                rawText = b.actCategory.text?.toString().orEmpty(),
                options = categoryOptions,
                allLabel = ALL_CATEGORIES_LABEL
            )

            selectedSubjectId = null
            refreshSubjectOptions()
            b.actSubject.setText(ALL_SUBJECTS_LABEL, false)
            applyFilters()
        }

        b.actSubject.setOnItemClickListener { _, _, _, _ ->
            selectedSubjectId = resolveOptionId(
                rawText = b.actSubject.text?.toString().orEmpty(),
                options = subjectOptions,
                allLabel = ALL_SUBJECTS_LABEL
            )
            applyFilters()
        }

        b.actLanguage.setOnItemClickListener { _, _, _, _ ->
            selectedLanguageId = resolveOptionId(
                rawText = b.actLanguage.text?.toString().orEmpty(),
                options = languageOptions,
                allLabel = ALL_LANGUAGES_LABEL
            )
            applyFilters()
        }

        b.btnResetFilters.setOnClickListener {
            searchQuery = ""
            b.etSearch.setText("")
            filterFree = false
            filterPremium = false
            b.chipFree.isChecked = false
            b.chipPremium.isChecked = false

            selectedCategoryId = null
            selectedSubjectId = null
            selectedLanguageId = null

            b.actCategory.setText(ALL_CATEGORIES_LABEL, false)
            b.actLanguage.setText(ALL_LANGUAGES_LABEL, false)
            refreshSubjectOptions()
            b.actSubject.setText(ALL_SUBJECTS_LABEL, false)

            applyFilters()
        }

        loadMasterFilterOptions()
        loadDecks()
    }

    override fun onResume() {
        super.onResume()
        loadMasterFilterOptions()
        loadDecks()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun loadMasterFilterOptions() {
        refreshCategoryOptions()
        refreshLanguageOptions()
        refreshSubjectOptions()
    }

    private fun refreshCategoryOptions() {
        categoryOptions = categoryDao.getSelectableCategories()
            .map { FilterOption(it.id, it.name) }
            .sortedBy { it.label.lowercase() }

        val labels = mutableListOf(ALL_CATEGORIES_LABEL).apply {
            addAll(categoryOptions.map { it.label })
        }

        b.actCategory.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )

        val valid = selectedCategoryId != null && categoryOptions.any { it.id == selectedCategoryId }
        if (!valid) {
            selectedCategoryId = null
            b.actCategory.setText(ALL_CATEGORIES_LABEL, false)
        } else {
            val selected = categoryOptions.firstOrNull { it.id == selectedCategoryId }?.label
                ?: ALL_CATEGORIES_LABEL
            b.actCategory.setText(selected, false)
        }
    }

    private fun refreshSubjectOptions() {
        subjectOptions = if (selectedCategoryId != null && selectedCategoryId!! > 0L) {
            subjectDao.getSelectableSubjects(selectedCategoryId)
                .map { FilterOption(it.id, it.name) }
                .sortedBy { it.label.lowercase() }
        } else {
            emptyList()
        }

        val labels = mutableListOf(ALL_SUBJECTS_LABEL).apply {
            addAll(subjectOptions.map { it.label })
        }

        b.actSubject.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )

        val hasCategory = selectedCategoryId != null
        val valid = selectedSubjectId != null && subjectOptions.any { it.id == selectedSubjectId }

        if (!valid) {
            selectedSubjectId = null
            b.actSubject.setText(ALL_SUBJECTS_LABEL, false)
        } else {
            val selected = subjectOptions.firstOrNull { it.id == selectedSubjectId }?.label
                ?: ALL_SUBJECTS_LABEL
            b.actSubject.setText(selected, false)
        }

        b.actSubject.isEnabled = hasCategory
        b.tilSubject.isEnabled = hasCategory

        b.tvSubjectNote.text = when {
            !hasCategory -> "Choose a category first to see subjects."
            subjectOptions.isEmpty() -> "No active subjects in this category."
            else -> "Optional subject filter."
        }
    }

    private fun refreshLanguageOptions() {
        languageOptions = languageDao.getSelectableLanguages()
            .map { FilterOption(it.id, it.name) }
            .sortedBy { it.label.lowercase() }

        val labels = mutableListOf(ALL_LANGUAGES_LABEL).apply {
            addAll(languageOptions.map { it.label })
        }

        b.actLanguage.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )

        val valid = selectedLanguageId != null && languageOptions.any { it.id == selectedLanguageId }
        if (!valid) {
            selectedLanguageId = null
            b.actLanguage.setText(ALL_LANGUAGES_LABEL, false)
        } else {
            val selected = languageOptions.firstOrNull { it.id == selectedLanguageId }?.label
                ?: ALL_LANGUAGES_LABEL
            b.actLanguage.setText(selected, false)
        }
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

        if (filterFree && !filterPremium) {
            list = list.filter { !it.isPremium }
        }
        if (filterPremium && !filterFree) {
            list = list.filter { it.isPremium }
        }

        selectedCategoryId?.let { categoryId ->
            list = list.filter { it.categoryId == categoryId }
        }

        selectedSubjectId?.let { subjectId ->
            list = list.filter { it.subjectId == subjectId }
        }

        selectedLanguageId?.let { languageId ->
            list = list.filter { it.languageId == languageId }
        }

        if (q.isNotBlank()) {
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.ownerName.lowercase().contains(q) ||
                        it.ownerEmail.lowercase().contains(q) ||
                        (it.description ?: "").lowercase().contains(q) ||
                        (it.categoryName ?: "").lowercase().contains(q) ||
                        (it.subjectName ?: "").lowercase().contains(q) ||
                        (it.languageName ?: "").lowercase().contains(q)
            }
        }

        adapter.submitList(list)
        b.tvCount.text = "${list.size} deck(s) found"

        val empty = list.isEmpty()
        b.groupEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun resolveOptionId(
        rawText: String,
        options: List<FilterOption>,
        allLabel: String
    ): Long? {
        val clean = rawText.trim()
        if (clean.isBlank() || clean == allLabel) return null
        return options.firstOrNull { it.label.equals(clean, ignoreCase = true) }?.id
    }

    private fun openDeck(row: ExploreDao.PublicDeckCatalogRow) {
        if (row.isLocked) {
            startActivity(
                Intent(requireContext(), PremiumDemoActivity::class.java)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, row.deckId)
                    .putExtra(PremiumDemoActivity.EXTRA_RETURN_READ_ONLY_PUBLIC, true)
            )
            return
        }

        startActivity(
            Intent(requireContext(), DeckCardsActivity::class.java)
                .putExtra(DeckCardsActivity.EXTRA_DECK_ID, row.deckId)
                .putExtra(DeckCardsActivity.EXTRA_READ_ONLY_PUBLIC, true)
        )
    }

    private fun copyDeck(row: ExploreDao.PublicDeckCatalogRow) {
        val me = session.load() ?: return

        if (row.isLocked) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Premium deck")
                .setMessage("Upgrade to Premium to copy this deck to your library.")
                .setPositiveButton("Go Premium") { _, _ ->
                    startActivity(
                        Intent(requireContext(), PremiumDemoActivity::class.java)
                            .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, row.deckId)
                            .putExtra(PremiumDemoActivity.EXTRA_RETURN_READ_ONLY_PUBLIC, true)
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Copy deck?")
            .setMessage("\"${row.title}\" by ${row.ownerName} will be added to your library.")
            .setPositiveButton("Copy") { _, _ ->
                val newId = runCatching {
                    exploreDao.copyPublicDeckToUser(
                        sourceDeckId = row.deckId,
                        targetUserId = me.id
                    )
                }.getOrDefault(-1L)

                when {
                    newId > 0L -> Snackbar.make(
                        b.root,
                        "Deck copied to your library.",
                        Snackbar.LENGTH_LONG
                    ).show()

                    else -> Snackbar.make(
                        b.root,
                        "Could not copy deck. Try again.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun previewDeck(row: ExploreDao.PublicDeckCatalogRow) {
        if (row.isLocked) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Premium: ${row.title}")
                .setMessage(
                    "This is a premium deck.\n\n" +
                            "Upgrade to open the full deck or copy it to your library.\n\n" +
                            "You can still use the Report button on the deck card to submit a content report."
                )
                .setPositiveButton("Go Premium") { _, _ ->
                    startActivity(
                        Intent(requireContext(), PremiumDemoActivity::class.java)
                            .putExtra(PremiumDemoActivity.EXTRA_RETURN_DECK_ID, row.deckId)
                            .putExtra(PremiumDemoActivity.EXTRA_RETURN_READ_ONLY_PUBLIC, true)
                    )
                }
                .setNegativeButton("Close", null)
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

        val meta = buildList {
            row.categoryName?.takeIf { it.isNotBlank() }?.let { add("Category: $it") }
            row.subjectName?.takeIf { it.isNotBlank() }?.let { add("Subject: $it") }
            row.languageName?.takeIf { it.isNotBlank() }?.let { add("Language: $it") }
        }.joinToString("\n")

        val preview = cards.take(5)
            .joinToString("\n\n") { "Q: ${it.question}\nA: ${it.answer}" }
        val suffix = if (cards.size > 5) "\n\n...and ${cards.size - 5} more cards." else ""

        val message = buildString {
            if (meta.isNotBlank()) {
                append(meta)
                append("\n\n")
            }
            append(preview)
            append(suffix)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Preview: ${row.title}")
            .setMessage(message)
            .setPositiveButton("Open deck") { _, _ -> openDeck(row) }
            .setNeutralButton("Copy to Library") { _, _ -> copyDeck(row) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun reportDeck(row: ExploreDao.PublicDeckCatalogRow) {
        val me = session.load()
        if (me == null) {
            Snackbar.make(b.root, "Please log in first.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val reasons = loadContentReportReasons()
        if (reasons.isEmpty()) {
            Snackbar.make(b.root, "No content report reasons available.", Snackbar.LENGTH_LONG).show()
            return
        }

        val labels = reasons.map { it.name }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report deck")
            .setItems(labels) { _, which ->
                val selected = reasons[which]
                showReportDetailsDialog(row, me.id.toLong(), selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReportDetailsDialog(
        row: ExploreDao.PublicDeckCatalogRow,
        reporterUserId: Long,
        reason: ReportReasonChoice
    ) {
        val input = EditText(requireContext()).apply {
            hint = "Add details (optional)"
            minLines = 3
            maxLines = 5
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report: ${row.title}")
            .setMessage("Reason: ${reason.name}")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val details = input.text?.toString()?.trim().orEmpty()
                val ok = submitContentReport(
                    reporterUserId = reporterUserId,
                    deckId = row.deckId,
                    reason = reason,
                    details = details
                )

                if (ok) {
                    Snackbar.make(b.root, "Report submitted.", Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(b.root, "Could not submit report.", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadContentReportReasons(): List<ReportReasonChoice> {
        val db = dbHelper.readableDatabase
        val out = mutableListOf<ReportReasonChoice>()

        val selection = "${DbContract.RRISACTIVE} = 1 AND ${DbContract.RRTYPE} = ?"
        val selectionArgs = arrayOf(DbContract.RR_TYPE_CONTENT)

        db.query(
            DbContract.TREPORTREASONS,
            arrayOf(DbContract.RRID, DbContract.RRNAME, DbContract.RRDESCRIPTION),
            selection,
            selectionArgs,
            null,
            null,
            "${DbContract.RRSORTORDER} ASC, ${DbContract.RRNAME} ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out += ReportReasonChoice(
                    id = c.getLong(c.getColumnIndexOrThrow(DbContract.RRID)),
                    name = c.getString(c.getColumnIndexOrThrow(DbContract.RRNAME)),
                    description = c.getString(c.getColumnIndexOrThrow(DbContract.RRDESCRIPTION))
                )
            }
        }

        if (out.isNotEmpty()) return out

        return listOf(
            ReportReasonChoice(0L, "Incorrect content", null),
            ReportReasonChoice(0L, "Spam", null),
            ReportReasonChoice(0L, "Offensive content", null),
            ReportReasonChoice(0L, "Copyright issue", null),
            ReportReasonChoice(0L, "Other", null)
        )
    }

    private fun submitContentReport(
        reporterUserId: Long,
        deckId: Long,
        reason: ReportReasonChoice,
        details: String?
    ): Boolean {
        return runCatching {
            val values = ContentValues().apply {
                put(DbContract.RREPORTERUSERID, reporterUserId)
                put(DbContract.RDECKID, deckId)
                if (reason.id > 0L) {
                    put(DbContract.RREASONID, reason.id)
                } else {
                    putNull(DbContract.RREASONID)
                }
                put(DbContract.RREASON, reason.name)
                if (details.isNullOrBlank()) {
                    putNull(DbContract.RDETAILS)
                } else {
                    put(DbContract.RDETAILS, details)
                }
                put(DbContract.RSTATUS, DbContract.REPORTOPEN)
                put(DbContract.RCREATEDAT, System.currentTimeMillis())
            }

            dbHelper.writableDatabase.insert(DbContract.TREPORTS, null, values) > 0L
        }.getOrElse { false }
    }

    private data class FilterOption(
        val id: Long,
        val label: String
    )

    private data class ReportReasonChoice(
        val id: Long,
        val name: String,
        val description: String?
    )

    private class PublicDeckAdapter(
        private val onOpen: (ExploreDao.PublicDeckCatalogRow) -> Unit,
        private val onCopy: (ExploreDao.PublicDeckCatalogRow) -> Unit,
        private val onPreview: (ExploreDao.PublicDeckCatalogRow) -> Unit,
        private val onReport: (ExploreDao.PublicDeckCatalogRow) -> Unit
    ) : ListAdapter<ExploreDao.PublicDeckCatalogRow, PublicDeckAdapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemPublicDeckBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding, onOpen, onCopy, onPreview, onReport)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class VH(
            private val b: ItemPublicDeckBinding,
            private val onOpen: (ExploreDao.PublicDeckCatalogRow) -> Unit,
            private val onCopy: (ExploreDao.PublicDeckCatalogRow) -> Unit,
            private val onPreview: (ExploreDao.PublicDeckCatalogRow) -> Unit,
            private val onReport: (ExploreDao.PublicDeckCatalogRow) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(row: ExploreDao.PublicDeckCatalogRow) {
                b.tvTitle.text = if (row.isPremium) "⭐ ${row.title}" else row.title
                b.tvOwner.text = "by ${row.ownerName}"
                b.tvDesc.text = row.description ?: "No description."
                b.tvCardCount.text = "${row.cardCount} cards"

                if (row.categoryName.isNullOrBlank()) {
                    b.chipCategory.visibility = View.GONE
                } else {
                    b.chipCategory.visibility = View.VISIBLE
                    b.chipCategory.text = row.categoryName
                }

                if (row.subjectName.isNullOrBlank()) {
                    b.chipSubject.visibility = View.GONE
                } else {
                    b.chipSubject.visibility = View.VISIBLE
                    b.chipSubject.text = row.subjectName
                }

                if (row.languageName.isNullOrBlank()) {
                    b.chipLanguage.visibility = View.GONE
                } else {
                    b.chipLanguage.visibility = View.VISIBLE
                    b.chipLanguage.text = row.languageName
                }

                if (row.isLocked) {
                    b.tvLocked.visibility = View.VISIBLE
                    b.tvLocked.text = "🔒 Premium"
                    b.btnOpen.text = "Unlock"
                    b.btnCopy.text = "Locked"
                } else {
                    b.tvLocked.visibility = View.GONE
                    b.btnOpen.text = "Open"
                    b.btnCopy.text = "Copy"
                }

                b.btnOpen.setOnClickListener { onOpen(row) }
                b.btnCopy.setOnClickListener { onCopy(row) }
                b.btnPreview.setOnClickListener { onPreview(row) }
                b.btnReport.setOnClickListener { onReport(row) }
                b.root.setOnClickListener { onOpen(row) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<ExploreDao.PublicDeckCatalogRow>() {
            override fun areItemsTheSame(
                oldItem: ExploreDao.PublicDeckCatalogRow,
                newItem: ExploreDao.PublicDeckCatalogRow
            ): Boolean = oldItem.deckId == newItem.deckId

            override fun areContentsTheSame(
                oldItem: ExploreDao.PublicDeckCatalogRow,
                newItem: ExploreDao.PublicDeckCatalogRow
            ): Boolean = oldItem == newItem
        }
    }

    private companion object {
        private const val ALL_CATEGORIES_LABEL = "All categories"
        private const val ALL_SUBJECTS_LABEL = "All subjects"
        private const val ALL_LANGUAGES_LABEL = "All languages"
    }
}