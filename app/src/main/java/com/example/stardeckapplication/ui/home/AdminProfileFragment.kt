package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminProfileBinding
import com.example.stardeckapplication.db.AdminDashboardDao
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.DeckDao
import com.example.stardeckapplication.db.ModerationDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.db.UserDao
import com.example.stardeckapplication.ui.auth.LoginActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AdminProfileFragment : Fragment(R.layout.fragment_admin_profile) {

    private var _b: FragmentAdminProfileBinding? = null
    private val b get() = _b!!
    private val session by lazy { SessionManager(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentAdminProfileBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_ADMIN) {
            requireActivity().finish()
            return
        }

        // Avatar & info
        b.tvAvatarLetter.text = me.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        b.tvName.text         = me.name
        b.tvNameValue.text    = me.name
        b.tvEmail.text        = me.email

        // Stats using correct DAOs
        val db       = StarDeckDbHelper(requireContext())
        val snapshot = AdminDashboardDao(db).getSummarySnapshot()
        b.tvStatUsers.text   = snapshot.totalUsers.toString()
        b.tvStatDecks.text   = snapshot.totalDecks.toString()
        b.tvStatReports.text = snapshot.openReports.toString()

        // Change Password — inline dialog (no separate Activity needed)
        b.rowChangePassword.setOnClickListener {
            showChangePasswordDialog(db, me.id)
        }

        // About
        b.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("About StarDeck")
                .setMessage("StarDeck v1.0\nA flashcard learning platform.")
                .setPositiveButton("OK", null)
                .show()
        }

        // Logout
        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout?")
                .setMessage("You will need to log in again.")
                .setPositiveButton("Logout") { _, _ ->
                    session.clear()
                    startActivity(
                        Intent(requireContext(), LoginActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showChangePasswordDialog(db: StarDeckDbHelper, userId: Long) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val current = dialogView.findViewById<TextInputEditText>(R.id.etCurrentPassword)
                    .text.toString().toCharArray()
                val newPw   = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
                    .text.toString().toCharArray()
                val confirm = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)
                    .text.toString()

                if (newPw.concatToString() != confirm) {
                    Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                UserDao(db).updatePassword(userId, newPw, false)
                Toast.makeText(requireContext(), "Password updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}