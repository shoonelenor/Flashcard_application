package com.example.stardeckapplication.ui.cards

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.CardDao
import com.example.stardeckapplication.db.CardDao.Card
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class DeckCardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DECK_ID          = "deck_id"
        const val EXTRA_READ_ONLY_PUBLIC = "read_only_public"
    }

    // ── DAO & state ───────────────────────────────────────────────────────────
    private lateinit var cardDao: CardDao
    private var deckId: Long      = -1L
    private var deckTitle: String = ""
    private var isReadOnly: Boolean = false

    private var pendingFrontImagePath: String? = null
    private var pendingBackImagePath:  String? = null
    private var editFrontImagePath: String?    = null
    private var editBackImagePath:  String?    = null
    private var activePickerSlot: String       = ""

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var toolbar:             MaterialToolbar
    private lateinit var tvCount:             TextView
    private lateinit var btnStudy:            MaterialButton
    private lateinit var btnQuiz:             MaterialButton
    private lateinit var btnReport:           MaterialButton
    private lateinit var rvCards:             RecyclerView
    private lateinit var groupEmpty:          View
    private lateinit var fabAdd:              FloatingActionButton
    private lateinit var cardAddForm:         MaterialCardView
    private lateinit var btnCloseForm:        ImageButton

    // Add-card form fields
    private lateinit var etFront:             EditText
    private lateinit var etBack:              EditText
    private lateinit var imgFrontPreview:     ImageView
    private lateinit var imgBackPreview:      ImageView
    private lateinit var btnAddFrontImage:    MaterialButton
    private lateinit var btnAddBackImage:     MaterialButton
    private lateinit var btnRemoveFrontImage: ImageButton
    private lateinit var btnRemoveBackImage:  ImageButton
    private lateinit var btnSaveCard:         MaterialButton

    // Edit-dialog views
    private var editImgFrontPreview: ImageView? = null
    private var editImgBackPreview:  ImageView? = null

    // ── Image picker ──────────────────────────────────────────────────────────
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            val savedPath = saveImageToInternalStorage(uri) ?: return@registerForActivityResult
            when (activePickerSlot) {
                "add_front"  -> {
                    pendingFrontImagePath = savedPath
                    imgFrontPreview.setImageURI(uri)
                    imgFrontPreview.visibility     = View.VISIBLE
                    btnRemoveFrontImage.visibility = View.VISIBLE
                }
                "add_back"   -> {
                    pendingBackImagePath = savedPath
                    imgBackPreview.setImageURI(uri)
                    imgBackPreview.visibility    = View.VISIBLE
                    btnRemoveBackImage.visibility = View.VISIBLE
                }
                "edit_front" -> {
                    editFrontImagePath = savedPath
                    editImgFrontPreview?.setImageURI(uri)
                    editImgFrontPreview?.visibility = View.VISIBLE
                }
                "edit_back"  -> {
                    editBackImagePath = savedPath
                    editImgBackPreview?.setImageURI(uri)
                    editImgBackPreview?.visibility  = View.VISIBLE
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pickImageLauncher.launch("image/*")
            else Toast.makeText(this, "Permission denied – cannot pick images", Toast.LENGTH_SHORT).show()
        }

    // ── Adapter ───────────────────────────────────────────────────────────────
    private lateinit var adapter: CardListAdapter
    private val cardList = mutableListOf<Card>()

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck_cards)

        deckId     = intent.getLongExtra(EXTRA_DECK_ID, -1L)
        deckTitle  = intent.getStringExtra("deck_title") ?: ""
        isReadOnly = intent.getBooleanExtra(EXTRA_READ_ONLY_PUBLIC, false)
        if (deckId == -1L) { finish(); return }

        cardDao = CardDao(this)

        bindViews()
        setupToolbar()
        setupRecyclerView()
        loadCards()
        setupReadOnlyMode()
        setupFab()
        setupFormButtons()
        setupActionButtons()
    }

    // ── Binding ───────────────────────────────────────────────────────────────
    private fun bindViews() {
        toolbar             = findViewById(R.id.toolbar)
        tvCount             = findViewById(R.id.tvCount)
        btnStudy            = findViewById(R.id.btnStudy)
        btnQuiz             = findViewById(R.id.btnQuiz)
        btnReport           = findViewById(R.id.btnReport)
        rvCards             = findViewById(R.id.rvCards)
        groupEmpty          = findViewById(R.id.groupEmpty)
        fabAdd              = findViewById(R.id.fabAdd)
        cardAddForm         = findViewById(R.id.cardAddForm)
        btnCloseForm        = findViewById(R.id.btnCloseForm)
        etFront             = findViewById(R.id.etFront)
        etBack              = findViewById(R.id.etBack)
        imgFrontPreview     = findViewById(R.id.imgFrontPreview)
        imgBackPreview      = findViewById(R.id.imgBackPreview)
        btnAddFrontImage    = findViewById(R.id.btnAddFrontImage)
        btnAddBackImage     = findViewById(R.id.btnAddBackImage)
        btnRemoveFrontImage = findViewById(R.id.btnRemoveFrontImage)
        btnRemoveBackImage  = findViewById(R.id.btnRemoveBackImage)
        btnSaveCard         = findViewById(R.id.btnSaveCard)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = deckTitle
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupReadOnlyMode() {
        if (isReadOnly) {
            // In read-only/public mode: hide FAB & Quiz, show Report
            fabAdd.hide()
            btnQuiz.visibility   = View.GONE
            btnReport.visibility = View.VISIBLE
        } else {
            // Owner mode: show Quiz button (only if deck has >= 2 cards)
            btnReport.visibility = View.GONE
        }
    }

    // ── FAB / form panel ──────────────────────────────────────────────────────
    private fun setupFab() {
        fabAdd.setOnClickListener { showAddForm() }
        btnCloseForm.setOnClickListener { dismissAddForm() }
        findViewById<MaterialButton>(R.id.btnCreateFirst).setOnClickListener { showAddForm() }
    }

    private fun showAddForm() {
        cardAddForm.visibility = View.VISIBLE
        fabAdd.hide()
    }

    private fun dismissAddForm() {
        cardAddForm.visibility = View.GONE
        fabAdd.show()
        etFront.text?.clear()
        etBack.text?.clear()
        clearAddFormImages()
    }

    // ── Study / Quiz / Report buttons ─────────────────────────────────────────
    private fun setupActionButtons() {
        // Study — uses "extra_deck_id" as required by StudyActivity
        btnStudy.setOnClickListener {
            val intent = android.content.Intent(
                this,
                com.example.stardeckapplication.ui.study.StudyActivity::class.java
            )
            intent.putExtra("extra_deck_id", deckId)
            intent.putExtra("extra_read_only_public", isReadOnly)
            startActivity(intent)
        }

        // Quiz — uses "extra_deck_id" as required by QuizActivity
        btnQuiz.setOnClickListener {
            if (cardList.size < 2) {
                Toast.makeText(this, "You need at least 2 cards to start a quiz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = android.content.Intent(
                this,
                com.example.stardeckapplication.ui.quiz.QuizActivity::class.java
            )
            intent.putExtra("extra_deck_id", deckId)
            startActivity(intent)
        }

        // Report — show dialog (no separate ReportActivity found)
        btnReport.setOnClickListener {
            showReportDialog()
        }
    }

    private fun showReportDialog() {
        val reasons = arrayOf(
            "Incorrect information",
            "Inappropriate content",
            "Duplicate deck",
            "Spam",
            "Other"
        )
        var selectedReason = reasons[0]
        android.app.AlertDialog.Builder(this)
            .setTitle("Report this deck")
            .setSingleChoiceItems(reasons, 0) { _, which -> selectedReason = reasons[which] }
            .setPositiveButton("Submit Report") { _, _ ->
                Toast.makeText(this, "Report submitted: $selectedReason", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Form buttons ──────────────────────────────────────────────────────────
    private fun setupFormButtons() {
        btnAddFrontImage.setOnClickListener {
            activePickerSlot = "add_front"
            launchImagePicker()
        }
        btnAddBackImage.setOnClickListener {
            activePickerSlot = "add_back"
            launchImagePicker()
        }
        btnRemoveFrontImage.setOnClickListener {
            pendingFrontImagePath = null
            imgFrontPreview.setImageDrawable(null)
            imgFrontPreview.visibility     = View.GONE
            btnRemoveFrontImage.visibility = View.GONE
        }
        btnRemoveBackImage.setOnClickListener {
            pendingBackImagePath = null
            imgBackPreview.setImageDrawable(null)
            imgBackPreview.visibility     = View.GONE
            btnRemoveBackImage.visibility = View.GONE
        }
        btnSaveCard.setOnClickListener { saveCard() }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = CardListAdapter(
            cards    = cardList,
            onEdit   = { card -> showEditDialog(card) },
            onDelete = { card -> confirmDelete(card) }
        )
        rvCards.layoutManager = LinearLayoutManager(this)
        rvCards.adapter       = adapter
    }

    private fun loadCards() {
        cardList.clear()
        cardList.addAll(cardDao.getCardListByDeck(deckId))
        adapter.notifyDataSetChanged()
        val count = cardList.size
        tvCount.text          = "$count card${if (count == 1) "" else "s"}"
        groupEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
        rvCards.visibility    = if (count == 0) View.GONE    else View.VISIBLE
        // Show Quiz button only if owner and has enough cards
        if (!isReadOnly) {
            btnQuiz.visibility = if (count >= 2) View.VISIBLE else View.GONE
        }
    }

    // ── Save new card ─────────────────────────────────────────────────────────
    private fun saveCard() {
        val front = etFront.text.toString().trim()
        val back  = etBack.text.toString().trim()
        if (front.isEmpty() || back.isEmpty()) {
            Toast.makeText(this, "Front and back text are required", Toast.LENGTH_SHORT).show()
            return
        }
        val result = cardDao.insertCard(
            deckId         = deckId,
            front          = front,
            back           = back,
            frontImagePath = pendingFrontImagePath,
            backImagePath  = pendingBackImagePath
        )
        if (result != -1L) {
            dismissAddForm()
            loadCards()
            Toast.makeText(this, "Card added", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to add card", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAddFormImages() {
        pendingFrontImagePath = null
        pendingBackImagePath  = null
        imgFrontPreview.setImageDrawable(null)
        imgFrontPreview.visibility     = View.GONE
        imgBackPreview.setImageDrawable(null)
        imgBackPreview.visibility      = View.GONE
        btnRemoveFrontImage.visibility = View.GONE
        btnRemoveBackImage.visibility  = View.GONE
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    private fun showEditDialog(card: Card) {
        val dialogView      = layoutInflater.inflate(R.layout.dialog_edit_card, null)
        val etFrontEdit     = dialogView.findViewById<EditText>(R.id.etFrontEdit)
        val etBackEdit      = dialogView.findViewById<EditText>(R.id.etBackEdit)
        editImgFrontPreview = dialogView.findViewById(R.id.imgFrontPreviewEdit)
        editImgBackPreview  = dialogView.findViewById(R.id.imgBackPreviewEdit)
        val btnEditFront    = dialogView.findViewById<android.widget.Button>(R.id.btnEditFrontImage)
        val btnEditBack     = dialogView.findViewById<android.widget.Button>(R.id.btnEditBackImage)
        val btnRemFront     = dialogView.findViewById<ImageButton>(R.id.btnRemoveFrontImageEdit)
        val btnRemBack      = dialogView.findViewById<ImageButton>(R.id.btnRemoveBackImageEdit)

        etFrontEdit.setText(card.front)
        etBackEdit.setText(card.back)
        editFrontImagePath = card.frontImagePath
        editBackImagePath  = card.backImagePath

        card.frontImagePath?.let { path ->
            val f = File(path); if (f.exists()) {
                editImgFrontPreview?.setImageURI(Uri.fromFile(f))
                editImgFrontPreview?.visibility = View.VISIBLE
                btnRemFront.visibility          = View.VISIBLE
            }
        }
        card.backImagePath?.let { path ->
            val f = File(path); if (f.exists()) {
                editImgBackPreview?.setImageURI(Uri.fromFile(f))
                editImgBackPreview?.visibility  = View.VISIBLE
                btnRemBack.visibility           = View.VISIBLE
            }
        }

        btnEditFront.setOnClickListener { activePickerSlot = "edit_front"; launchImagePicker() }
        btnEditBack.setOnClickListener  { activePickerSlot = "edit_back";  launchImagePicker() }
        btnRemFront.setOnClickListener {
            editFrontImagePath = null
            editImgFrontPreview?.setImageDrawable(null)
            editImgFrontPreview?.visibility = View.GONE
            btnRemFront.visibility          = View.GONE
        }
        btnRemBack.setOnClickListener {
            editBackImagePath = null
            editImgBackPreview?.setImageDrawable(null)
            editImgBackPreview?.visibility = View.GONE
            btnRemBack.visibility          = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Card")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newFront = etFrontEdit.text.toString().trim()
                val newBack  = etBackEdit.text.toString().trim()
                if (newFront.isEmpty() || newBack.isEmpty()) {
                    Toast.makeText(this, "Front and back text are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cardDao.updateCard(
                    cardId         = card.id,
                    front          = newFront,
                    back           = newBack,
                    frontImagePath = editFrontImagePath,
                    backImagePath  = editBackImagePath
                )
                loadCards()
                Toast.makeText(this, "Card updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    private fun confirmDelete(card: Card) {
        AlertDialog.Builder(this)
            .setTitle("Delete Card")
            .setMessage("Delete this card? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                cardDao.deleteCard(card.id)
                loadCards()
                Toast.makeText(this, "Card deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Image helpers ─────────────────────────────────────────────────────────
    private fun launchImagePicker() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        when {
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED -> pickImageLauncher.launch("image/*")
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Photo access is needed to add images", Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> requestPermissionLauncher.launch(permission)
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val dir  = File(filesDir, "card_images").apply { mkdirs() }
            val file = File(dir, "${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) { e.printStackTrace(); null }
    }
}
