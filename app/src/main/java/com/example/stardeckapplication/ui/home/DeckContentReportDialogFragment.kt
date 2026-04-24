package com.example.stardeckapplication.ui.home

import android.app.Dialog
import android.content.ContentValues
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.StarDeckDbHelper

class DeckContentReportDialogFragment : DialogFragment() {

    data class ReasonItem(val id: Int, val name: String)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val deckId = requireArguments().getInt(ARG_DECK_ID)
        val reporterUserId = requireArguments().getInt(ARG_REPORTER_USER_ID)

        val reasons = loadContentReasons()
        val reasonNames = reasons.map { it.name }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(20)
            setPadding(p, p, p, p)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val reasonSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                reasonNames
            )
        }

        val detailsInput = EditText(requireContext()).apply {
            hint = "Add more details (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            maxLines = 6
        }

        container.addView(reasonSpinner)
        container.addView(space())
        container.addView(detailsInput)

        return AlertDialog.Builder(requireContext())
            .setTitle("Report this deck")
            .setMessage("Choose a content-report reason for this deck.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit") { _, _ ->
                val selectedReason = reasons.getOrNull(reasonSpinner.selectedItemPosition)
                if (selectedReason == null) {
                    Toast.makeText(requireContext(), "No report reason available", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val details = detailsInput.text?.toString()?.trim().orEmpty()
                val ok = submitDeckReport(
                    deckId = deckId,
                    reporterUserId = reporterUserId,
                    reason = selectedReason,
                    details = details
                )

                if (ok) {
                    Toast.makeText(requireContext(), "Deck report submitted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to submit report", Toast.LENGTH_SHORT).show()
                }
            }
            .create()
    }

    private fun loadContentReasons(): List<ReasonItem> {
        val helper = StarDeckDbHelper(requireContext())
        val db = helper.readableDatabase

        val items = mutableListOf<ReasonItem>()

        val cursor = db.query(
            DbContract.TREPORTREASONS,
            arrayOf(DbContract.RRID, DbContract.RRNAME),
            "${DbContract.RRTYPE}=? AND ${DbContract.RRISACTIVE}=1",
            arrayOf(DbContract.RR_TYPE_CONTENT),
            null,
            null,
            "${DbContract.RRSORTORDER} ASC, ${DbContract.RRNAME} ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                items.add(
                    ReasonItem(
                        id = it.getInt(it.getColumnIndexOrThrow(DbContract.RRID)),
                        name = it.getString(it.getColumnIndexOrThrow(DbContract.RRNAME))
                    )
                )
            }
        }

        if (items.isNotEmpty()) return items

        return listOf(
            ReasonItem(0, "Incorrect content"),
            ReasonItem(0, "Spam"),
            ReasonItem(0, "Offensive content"),
            ReasonItem(0, "Copyright issue"),
            ReasonItem(0, "Other")
        )
    }

    private fun submitDeckReport(
        deckId: Int,
        reporterUserId: Int,
        reason: ReasonItem,
        details: String
    ): Boolean {
        return try {
            val helper = StarDeckDbHelper(requireContext())
            val db = helper.writableDatabase

            val values = ContentValues().apply {
                put(DbContract.RREPORTERUSERID, reporterUserId)
                put(DbContract.RDECKID, deckId)
                if (reason.id > 0) put(DbContract.RREASONID, reason.id)
                put(DbContract.RREASON, reason.name)
                if (details.isNotBlank()) {
                    put(DbContract.RDETAILS, details)
                } else {
                    putNull(DbContract.RDETAILS)
                }
                put(DbContract.RSTATUS, DbContract.REPORTOPEN)
                put(DbContract.RCREATEDAT, System.currentTimeMillis())
            }

            db.insert(DbContract.TREPORTS, null, values) != -1L
        } catch (_: Exception) {
            false
        }
    }

    private fun dp(value: Int): Int {
        val density = requireContext().resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun space(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(12)
            )
        }
    }

    companion object {
        private const val ARG_DECK_ID = "deck_id"
        private const val ARG_REPORTER_USER_ID = "reporter_user_id"

        fun newInstance(deckId: Int, reporterUserId: Int): DeckContentReportDialogFragment {
            return DeckContentReportDialogFragment().apply {
                arguments = bundleOf(
                    ARG_DECK_ID to deckId,
                    ARG_REPORTER_USER_ID to reporterUserId
                )
            }
        }
    }
}