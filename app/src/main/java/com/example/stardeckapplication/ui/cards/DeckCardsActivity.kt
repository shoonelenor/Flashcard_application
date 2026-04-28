package com.example.stardeckapplication.ui.cards

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.ActivityDeckCardsBinding
import com.example.stardeckapplication.databinding.DialogEditCardBinding
import com.example.stardeckapplication.databinding.DialogReportDeckBinding
import com.example.stardeckapplication.databinding.ItemCardBinding
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.DeckDao
import com.example.stardeckapplication.db.ReportReasonDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.ui.quiz.QuizActivity
import com.example.stardeckapplication.ui.study.StudyActivity
import com.example.stardeckapplication.util.AchievementSyncHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class DeckCardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID          = "extra_deck_id"
        const val EXTRA_READ_ONLY_PUBLIC = "extra_read_only_public"
    }

    private lateinit var b: ActivityDeckCardsBinding

    private val dbHelper         by lazy { StarDeckDbHelper(this) }
    private val deckDao          by lazy { DeckDao(dbHelper) }
    private val cardDao          by lazy { CardDao(dbHelper) }
    private val reportReasonDao  by lazy { ReportReasonDao(dbHelper) }
    private val session          by lazy { SessionManager(this) }
    private val achievementSync  by lazy { AchievementSyncHelper(dbHelper) }

    private var deckId          : Long    = -1L
    private var readOnlyPublic  : Boolean = false
    private var all             : List<CardDao.CardRow> = emptyList()

    // ── Image picker state ──────────────────────────────────────────────────
    // Which side is currently being picked: "front" or "back"
    private var pickTarget      : String  = "front"
    // Paths chosen in the currently-open dialog (reset on each dialog open)
    private var pickedFrontPath : String? = null
    private var pickedBackPath  : String? = null
    // Binding reference for the currently-open edit/create dialog
    private var activeDialogBinding : DialogEditCardBinding? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openGallery() else
            Snackbar.make(b.root, "Permission needed to pick images", Snackbar.LENGTH_LONG).show()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val path = saveImageToInternalStorage(uri) ?: return@registerForActivityResult
        val d    = activeDialogBinding ?: return@registerForActivityResult
        if (pickTarget == "front") {
            pickedFrontPath = path
            d.btnAddFrontImage.text = "Change Front Image"
            Glide.with(this).load(File(path)).centerCrop().into(d.imgFrontPreview)
            d.imgFrontPreview.visibility = View.VISIBLE
            d.btnClearFrontImage.visibility = View.VISIBLE
        } else {
            pickedBackPath = path
            d.btnAddBackImage.text = "Change Back Image"
            Glide.with(this).load(File(path)).centerCrop().into(d.imgBackPreview)
            d.imgBackPreview.visibility = View.VISIBLE
            d.btnClearBackImage.visibility = View.VISIBLE
        }
    }

    private val adapter by lazy {
        CardsAdapter(
            canEdit  = !readOnlyPublic,
            onEdit   = { showEditDialog(it) },
            onDelete = { confirmDelete(it) }
        )
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDeckCardsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) { finish(); return }

        deckId         = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        readOnlyPublic = intent.getBooleanExtra(EXTRA_READ_ONLY_PUBLIC, false)

        if (deckId <= 0L) { finish(); return }

        val title = if (readOnlyPublic)
            deckDao.getPublicDeckTitleForUser(me.id, deckId)
        else
            deckDao.getDeckTitleForOwner(me.id, deckId)

        if (title == null) {
            Snackbar.make(b.root, "Deck not found.", Snackbar.LENGTH_LONG).show()
            finish(); return
        }

        if (readOnlyPublic && deckDao.isDeckLockedForUser(me.id, deckId)) {
            Snackbar.make(b.root, "This deck is premium and is locked for your account.", Snackbar.LENGTH_LONG).show()
            finish(); return
        }

        supportActionBar?.title    = title
        supportActionBar?.subtitle = if (readOnlyPublic) "Public read-only deck" else null

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter       = adapter

        b.etSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }

        b.btnStudy.setOnClickListener {
            startActivity(
                Intent(this, StudyActivity::class.java)
                    .putExtra(StudyActivity.EXTRA_DECK_ID, deckId)
            )
        }

        b.btnQuiz.setOnClickListener {
            val cardCount = cardDao.getCardsForDeck(me.id, deckId).size
            if (cardCount < 2) {
                Snackbar.make(b.root, "You need at least 2 cards to start a quiz", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, QuizActivity::class.java)
                    .putExtra(QuizActivity.EXTRA_DECK_ID, deckId)
            )
        }

        if (readOnlyPublic) {
            b.fabAdd.visibility       = View.GONE
            b.btnCreateFirst.visibility = View.GONE
            b.btnReport.visibility    = View.VISIBLE
            b.btnReport.setOnClickListener { showReportDeckDialog() }
        } else {
            b.fabAdd.visibility         = View.VISIBLE
            b.btnCreateFirst.visibility = View.VISIBLE
            b.btnReport.visibility      = View.GONE
            b.fabAdd.setOnClickListener        { showCreateDialog() }
            b.btnCreateFirst.setOnClickListener { showCreateDialog() }
        }

        invalidateOptionsMenu()
        reload()
    }

    // ── Options menu ────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_deck_cards, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_report_deck)?.isVisible = readOnlyPublic
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home     -> { finish(); true }
            R.id.action_report_deck -> { showReportDeckDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Image helpers ───────────────────────────────────────────────────────

    private fun requestPickImage(target: String, dialog: DialogEditCardBinding) {
        pickTarget          = target
        activeDialogBinding = dialog

        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    /**
     * Copies the picked URI into app-private storage and returns the absolute path.
     * Returns null if the copy fails.
     */
    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val dir  = File(filesDir, "card_images").apply { mkdirs() }
            val file = File(dir, "${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Safely deletes an image file that this app created (no-op on null/empty). */
    private fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        try { File(path).delete() } catch (_: Exception) { }
    }

    // ── Load image into an ImageView in the dialog ───────────────────────────

    private fun loadImageIntoDialog(d: DialogEditCardBinding, path: String?, target: String) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (!file.exists()) return

        if (target == "front") {
            Glide.with(this).load(file).centerCrop().into(d.imgFrontPreview)
            d.imgFrontPreview.visibility  = View.VISIBLE
            d.btnClearFrontImage.visibility = View.VISIBLE
            d.btnAddFrontImage.text = "Change Front Image"
        } else {
            Glide.with(this).load(file).centerCrop().into(d.imgBackPreview)
            d.imgBackPreview.visibility   = View.VISIBLE
            d.btnClearBackImage.visibility  = View.VISIBLE
            d.btnAddBackImage.text = "Change Back Image"
        }
    }

    // ── Report dialog ───────────────────────────────────────────────────────

    private fun showReportDeckDialog() {
        val me      = session.load() ?: return
        val reasons = reportReasonDao.getActiveReasons()

        if (reasons.isEmpty()) { showLegacyReportDeckDialog(me.id); return }

        val d      = DialogReportDeckBinding.inflate(layoutInflater)
        val labels = reasons.map { it.name }

        d.actReason.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        if (labels.isNotEmpty()) d.actReason.setText(labels.first(), false)
        d.actReason.setOnClickListener { d.actReason.showDropDown() }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Report deck")
            .setView(d.root)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilReason.error  = null
                d.tilDetails.error = null

                val selectedName = d.actReason.text?.toString().orEmpty().trim()
                val selected     = reasons.firstOrNull { it.name.equals(selectedName, ignoreCase = true) }
                val details      = d.etDetails.text?.toString().orEmpty().trim()
                var ok           = true

                if (selected == null) { d.tilReason.error = "Please select a valid reason"; ok = false }
                if (details.length > 400) { d.tilDetails.error = "Max 400 characters"; ok = false }
                if (!ok) return@setOnClickListener

                val chosen = selected ?: return@setOnClickListener
                val result = deckDao.createDeckReport(
                    reporterUserId = me.id, deckId = deckId,
                    reasonId = chosen.id, reason = chosen.name,
                    details = details.ifBlank { null }
                )

                when {
                    result > 0L  -> { Snackbar.make(b.root, "Report submitted", Snackbar.LENGTH_SHORT).show(); dialog.dismiss() }
                    result == -2L -> Snackbar.make(b.root, "You cannot report your own deck.", Snackbar.LENGTH_LONG).show()
                    result == -3L -> Snackbar.make(b.root, "You already have an open report for this deck.", Snackbar.LENGTH_LONG).show()
                    result == -5L -> Snackbar.make(b.root, "This deck cannot be reported.", Snackbar.LENGTH_LONG).show()
                    else          -> Snackbar.make(b.root, "Could not submit report.", Snackbar.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun showLegacyReportDeckDialog(reporterUserId: Long) {
        val til = TextInputLayout(this).apply { hint = "Reason" }
        val et  = TextInputEditText(this).apply { minLines = 2; maxLines = 4 }
        til.addView(et)

        MaterialAlertDialogBuilder(this)
            .setTitle("Report deck")
            .setMessage("Describe the issue.")
            .setView(til)
            .setPositiveButton("Submit") { _, _ ->
                val reason = et.text?.toString().orEmpty().trim()
                if (reason.length < 3) {
                    Snackbar.make(b.root, "Please enter a short reason.", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val result = deckDao.createDeckReport(reporterUserId = reporterUserId, deckId = deckId, reason = reason, details = null)
                when {
                    result > 0L  -> Snackbar.make(b.root, "Report submitted", Snackbar.LENGTH_SHORT).show()
                    result == -2L -> Snackbar.make(b.root, "You cannot report your own deck.", Snackbar.LENGTH_LONG).show()
                    result == -3L -> Snackbar.make(b.root, "You already have an open report for this deck.", Snackbar.LENGTH_LONG).show()
                    result == -5L -> Snackbar.make(b.root, "This deck cannot be reported.", Snackbar.LENGTH_LONG).show()
                    else          -> Snackbar.make(b.root, "Could not submit report.", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Data ────────────────────────────────────────────────────────────────

    private fun reload() {
        val me = session.load() ?: return

        all = if (readOnlyPublic)
            cardDao.getPublicDeckCardsForUser(me.id, deckId)
        else
            cardDao.getCardsForDeck(me.id, deckId)

        val count = all.size
        b.tvCount.text    = if (count == 1) "1 card" else "$count cards"
        b.btnStudy.isEnabled = count > 0
        b.btnQuiz.isEnabled  = count >= 2

        filter(b.etSearch.text?.toString().orEmpty())
    }

    private fun filter(query: String) {
        val q        = query.trim().lowercase()
        val filtered = if (q.isBlank()) all else
            all.filter { it.front.lowercase().contains(q) || it.back.lowercase().contains(q) }

        adapter.submit(filtered)

        val empty = filtered.isEmpty()
        b.groupEmpty.visibility  = if (empty) View.VISIBLE else View.GONE
        b.recycler.visibility    = if (empty) View.GONE    else View.VISIBLE
    }

    // ── Create dialog ───────────────────────────────────────────────────────

    private fun showCreateDialog() {
        if (readOnlyPublic) return
        val me = session.load() ?: return

        // Reset picked paths for this new dialog session
        pickedFrontPath = null
        pickedBackPath  = null

        val d = DialogEditCardBinding.inflate(layoutInflater)
        d.tvTitle.text = "Create Card"
        activeDialogBinding = d

        // Wire image buttons
        d.btnAddFrontImage.setOnClickListener  { requestPickImage("front", d) }
        d.btnAddBackImage.setOnClickListener   { requestPickImage("back",  d) }
        d.btnClearFrontImage.setOnClickListener {
            deleteImageFile(pickedFrontPath)
            pickedFrontPath = null
            d.imgFrontPreview.setImageDrawable(null)
            d.imgFrontPreview.visibility   = View.GONE
            d.btnClearFrontImage.visibility = View.GONE
            d.btnAddFrontImage.text = "Add Front Image"
        }
        d.btnClearBackImage.setOnClickListener {
            deleteImageFile(pickedBackPath)
            pickedBackPath = null
            d.imgBackPreview.setImageDrawable(null)
            d.imgBackPreview.visibility   = View.GONE
            d.btnClearBackImage.visibility = View.GONE
            d.btnAddBackImage.text = "Add Back Image"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel") { _, _ ->
                // Clean up any images saved if user cancels
                deleteImageFile(pickedFrontPath)
                deleteImageFile(pickedBackPath)
                activeDialogBinding = null
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error  = null

                val front = d.etFront.text?.toString().orEmpty()
                val back  = d.etBack.text?.toString().orEmpty()

                if (!validate(front, back, d)) return@setOnClickListener

                val id = cardDao.createCard(
                    ownerUserId    = me.id,
                    deckId         = deckId,
                    front          = front,
                    back           = back,
                    frontImagePath = pickedFrontPath,
                    backImagePath  = pickedBackPath
                )

                if (id > 0L) {
                    val unlocked = achievementSync.syncForUser(me.id)
                    val msg = if (unlocked > 0) "Card created • $unlocked achievement(s) unlocked!" else "Card created"
                    Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
                    reload()
                    activeDialogBinding = null
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not create card", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    // ── Edit dialog ─────────────────────────────────────────────────────────

    private fun showEditDialog(card: CardDao.CardRow) {
        if (readOnlyPublic) return
        val me = session.load() ?: return

        // Seed picked paths from the existing card
        pickedFrontPath = card.frontImagePath
        pickedBackPath  = card.backImagePath

        // Flags to track if user explicitly cleared an existing image
        var clearFrontImage = false
        var clearBackImage  = false

        val d = DialogEditCardBinding.inflate(layoutInflater)
        d.tvTitle.text = "Edit Card"
        d.etFront.setText(card.front)
        d.etBack.setText(card.back)
        activeDialogBinding = d

        // Show existing images
        loadImageIntoDialog(d, card.frontImagePath, "front")
        loadImageIntoDialog(d, card.backImagePath,  "back")

        // Wire image buttons
        d.btnAddFrontImage.setOnClickListener  { requestPickImage("front", d) }
        d.btnAddBackImage.setOnClickListener   { requestPickImage("back",  d) }
        d.btnClearFrontImage.setOnClickListener {
            clearFrontImage = true
            pickedFrontPath = null
            d.imgFrontPreview.setImageDrawable(null)
            d.imgFrontPreview.visibility    = View.GONE
            d.btnClearFrontImage.visibility  = View.GONE
            d.btnAddFrontImage.text = "Add Front Image"
        }
        d.btnClearBackImage.setOnClickListener {
            clearBackImage = true
            pickedBackPath = null
            d.imgBackPreview.setImageDrawable(null)
            d.imgBackPreview.visibility    = View.GONE
            d.btnClearBackImage.visibility  = View.GONE
            d.btnAddBackImage.text = "Add Back Image"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ -> activeDialogBinding = null }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                d.tilFront.error = null
                d.tilBack.error  = null

                val front = d.etFront.text?.toString().orEmpty()
                val back  = d.etBack.text?.toString().orEmpty()

                if (!validate(front, back, d)) return@setOnClickListener

                val rows = cardDao.updateCard(
                    ownerUserId     = me.id,
                    deckId          = deckId,
                    cardId          = card.id,
                    front           = front,
                    back            = back,
                    frontImagePath  = pickedFrontPath,
                    backImagePath   = pickedBackPath,
                    clearFrontImage = clearFrontImage,
                    clearBackImage  = clearBackImage
                )

                if (rows == 1) {
                    Snackbar.make(b.root, "Card updated", Snackbar.LENGTH_SHORT).show()
                    reload()
                    activeDialogBinding = null
                    dialog.dismiss()
                } else {
                    Snackbar.make(b.root, "Could not update card", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    private fun confirmDelete(card: CardDao.CardRow) {
        if (readOnlyPublic) return
        val me = session.load() ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete card?")
            .setMessage("This card will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                val rows = cardDao.deleteCard(me.id, deckId, card.id)
                if (rows == 1) {
                    // Also clean up image files from internal storage
                    deleteImageFile(card.frontImagePath)
                    deleteImageFile(card.backImagePath)
                    Snackbar.make(b.root, "Card deleted", Snackbar.LENGTH_SHORT).show()
                    reload()
                } else {
                    Snackbar.make(b.root, "Could not delete card", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private fun validate(front: String, back: String, d: DialogEditCardBinding): Boolean {
        val f   = front.trim()
        val bck = back.trim()
        if (f.isBlank())        { d.tilFront.error = "Front is required";  return false }
        if (bck.isBlank())      { d.tilBack.error  = "Back is required";   return false }
        if (f.length > 120)     { d.tilFront.error = "Max 120 characters"; return false }
        if (bck.length > 500)   { d.tilBack.error  = "Max 500 characters"; return false }
        return true
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Adapter ─────────────────────────────────────────────────────────────

    private class CardsAdapter(
        private val canEdit  : Boolean,
        private val onEdit   : (CardDao.CardRow) -> Unit,
        private val onDelete : (CardDao.CardRow) -> Unit
    ) : RecyclerView.Adapter<CardsAdapter.VH>() {

        private val items    = mutableListOf<CardDao.CardRow>()
        private val expanded = mutableSetOf<Long>()

        fun submit(newItems: List<CardDao.CardRow>) {
            items.clear()
            items.addAll(newItems)
            expanded.retainAll(newItems.map { it.id }.toSet())
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding, expanded, canEdit, onEdit, onDelete) { id ->
                if (!expanded.add(id)) expanded.remove(id)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        class VH(
            private val b        : ItemCardBinding,
            private val expanded : Set<Long>,
            private val canEdit  : Boolean,
            private val onEdit   : (CardDao.CardRow) -> Unit,
            private val onDelete : (CardDao.CardRow) -> Unit,
            private val onToggle : (Long) -> Unit
        ) : RecyclerView.ViewHolder(b.root) {

            fun bind(card: CardDao.CardRow) {
                val isExpanded = expanded.contains(card.id)

                b.tvFront.text  = card.front
                b.tvBack.text   = card.back
                b.tvHint.text   = if (isExpanded) "Tap to hide answer" else "Tap to show answer"
                b.divider.visibility = if (isExpanded) View.VISIBLE else View.GONE
                b.tvBack.visibility  = if (isExpanded) View.VISIBLE else View.GONE

                // Front image — always shown if present
                if (!card.frontImagePath.isNullOrBlank()) {
                    val file = File(card.frontImagePath)
                    if (file.exists()) {
                        Glide.with(b.root.context).load(file).centerCrop().into(b.imgFront)
                        b.imgFront.visibility = View.VISIBLE
                    } else {
                        b.imgFront.visibility = View.GONE
                    }
                } else {
                    b.imgFront.visibility = View.GONE
                }

                // Back image — only shown when card is expanded
                if (isExpanded && !card.backImagePath.isNullOrBlank()) {
                    val file = File(card.backImagePath)
                    if (file.exists()) {
                        Glide.with(b.root.context).load(file).centerCrop().into(b.imgBack)
                        b.imgBack.visibility = View.VISIBLE
                    } else {
                        b.imgBack.visibility = View.GONE
                    }
                } else {
                    b.imgBack.visibility = View.GONE
                }

                b.btnEdit.visibility   = if (canEdit) View.VISIBLE else View.GONE
                b.btnDelete.visibility = if (canEdit) View.VISIBLE else View.GONE

                b.root.setOnClickListener  { onToggle(card.id) }
                b.btnEdit.setOnClickListener   { onEdit(card) }
                b.btnDelete.setOnClickListener { onDelete(card) }
            }
        }
    }
}
