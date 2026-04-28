package com.example.stardeckapplication.ui.cards

import android.Manifest
import android.app.Activity
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
import java.io.File

class DeckCardsActivity : AppCompatActivity() {

    // ── DAO & state ───────────────────────────────────────────────────────────
    private lateinit var cardDao: CardDao
    private var deckId: Long = -1L
    private var deckTitle: String = ""

    // image path chosen while filling the Add-Card form
    private var pendingFrontImagePath: String? = null
    private var pendingBackImagePath:  String? = null

    // image path chosen while filling the Edit-Card dialog
    private var editFrontImagePath: String? = null
    private var editBackImagePath:  String? = null

    // which image slot the picker was launched for ("add_front"/"add_back"/"edit_front"/"edit_back")
    private var activePickerSlot: String = ""

    // ── Views (Add-Card form) ─────────────────────────────────────────────────
    private lateinit var etFront: EditText
    private lateinit var etBack: EditText
    private lateinit var imgFrontPreview: ImageView
    private lateinit var imgBackPreview:  ImageView
    private lateinit var btnAddFrontImage: Button
    private lateinit var btnAddBackImage:  Button
    private lateinit var btnRemoveFrontImage: ImageButton
    private lateinit var btnRemoveBackImage:  ImageButton
    private lateinit var btnSaveCard: Button
    private lateinit var rvCards: RecyclerView

    // ── Edit-dialog views (set when dialog is shown) ──────────────────────────
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
                    imgFrontPreview.visibility  = View.VISIBLE
                    btnRemoveFrontImage.visibility = View.VISIBLE
                }
                "add_back"   -> {
                    pendingBackImagePath = savedPath
                    imgBackPreview.setImageURI(uri)
                    imgBackPreview.visibility   = View.VISIBLE
                    btnRemoveBackImage.visibility  = View.VISIBLE
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

    // ── Permission launcher (Android 13+ READ_MEDIA_IMAGES) ───────────────────
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

        deckId    = intent.getLongExtra("deck_id", -1L)
        deckTitle = intent.getStringExtra("deck_title") ?: ""
        if (deckId == -1L) { finish(); return }

        cardDao = CardDao(this)

        bindViews()
        setupRecyclerView()
        loadCards()

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
            imgBackPreview.visibility    = View.GONE
            btnRemoveBackImage.visibility = View.GONE
        }
        btnSaveCard.setOnClickListener { saveCard() }
    }

    // ── Binding ───────────────────────────────────────────────────────────────
    private fun bindViews() {
        etFront             = findViewById(R.id.etFront)
        etBack              = findViewById(R.id.etBack)
        imgFrontPreview     = findViewById(R.id.imgFrontPreview)
        imgBackPreview      = findViewById(R.id.imgBackPreview)
        btnAddFrontImage    = findViewById(R.id.btnAddFrontImage)
        btnAddBackImage     = findViewById(R.id.btnAddBackImage)
        btnRemoveFrontImage = findViewById(R.id.btnRemoveFrontImage)
        btnRemoveBackImage  = findViewById(R.id.btnRemoveBackImage)
        btnSaveCard         = findViewById(R.id.btnSaveCard)
        rvCards             = findViewById(R.id.rvCards)
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = CardListAdapter(
            cards      = cardList,
            onEdit     = { card -> showEditDialog(card) },
            onDelete   = { card -> confirmDelete(card) }
        )
        rvCards.layoutManager = LinearLayoutManager(this)
        rvCards.adapter = adapter
    }

    private fun loadCards() {
        cardList.clear()
        cardList.addAll(cardDao.getCardListByDeck(deckId))
        adapter.notifyDataSetChanged()
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
            etFront.text.clear()
            etBack.text.clear()
            clearAddFormImages()
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_card, null)
        val etFrontEdit     = dialogView.findViewById<EditText>(R.id.etFrontEdit)
        val etBackEdit      = dialogView.findViewById<EditText>(R.id.etBackEdit)
        editImgFrontPreview = dialogView.findViewById(R.id.imgFrontPreviewEdit)
        editImgBackPreview  = dialogView.findViewById(R.id.imgBackPreviewEdit)
        val btnEditFront    = dialogView.findViewById<Button>(R.id.btnEditFrontImage)
        val btnEditBack     = dialogView.findViewById<Button>(R.id.btnEditBackImage)
        val btnRemFront     = dialogView.findViewById<ImageButton>(R.id.btnRemoveFrontImageEdit)
        val btnRemBack      = dialogView.findViewById<ImageButton>(R.id.btnRemoveBackImageEdit)

        etFrontEdit.setText(card.front)
        etBackEdit.setText(card.back)
        editFrontImagePath = card.frontImagePath
        editBackImagePath  = card.backImagePath

        // load existing images
        card.frontImagePath?.let { path ->
            val f = File(path)
            if (f.exists()) {
                editImgFrontPreview?.setImageURI(Uri.fromFile(f))
                editImgFrontPreview?.visibility = View.VISIBLE
                btnRemFront.visibility = View.VISIBLE
            }
        }
        card.backImagePath?.let { path ->
            val f = File(path)
            if (f.exists()) {
                editImgBackPreview?.setImageURI(Uri.fromFile(f))
                editImgBackPreview?.visibility  = View.VISIBLE
                btnRemBack.visibility  = View.VISIBLE
            }
        }

        btnEditFront.setOnClickListener {
            activePickerSlot = "edit_front"
            launchImagePicker()
        }
        btnEditBack.setOnClickListener {
            activePickerSlot = "edit_back"
            launchImagePicker()
        }
        btnRemFront.setOnClickListener {
            editFrontImagePath = null
            editImgFrontPreview?.setImageDrawable(null)
            editImgFrontPreview?.visibility = View.GONE
            btnRemFront.visibility = View.GONE
        }
        btnRemBack.setOnClickListener {
            editBackImagePath = null
            editImgBackPreview?.setImageDrawable(null)
            editImgBackPreview?.visibility  = View.GONE
            btnRemBack.visibility  = View.GONE
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
                    PackageManager.PERMISSION_GRANTED -> {
                pickImageLauncher.launch("image/*")
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Photo access is needed to add images", Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> requestPermissionLauncher.launch(permission)
        }
    }

    /**
     * Copy the picked image into app-private internal storage and return the
     * absolute path. Returns null on any error.
     */
    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val dir = File(filesDir, "card_images").apply { mkdirs() }
            val file = File(dir, "${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
