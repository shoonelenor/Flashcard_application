package com.example.stardeckapplication.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentManagerProfileBinding
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.ui.auth.LoginActivity
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ManagerProfileFragment : Fragment(R.layout.fragment_manager_profile) {

    private var _b: FragmentManagerProfileBinding? = null
    private val b get() = _b!!
    private val session by lazy { SessionManager(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentManagerProfileBinding.bind(view)

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_MANAGER) {
            requireActivity().finish(); return
        }

        b.tvName.text = me.name
        b.tvEmail.text = me.email

        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout?")
                .setMessage("You will need to log in again.")
                .setPositiveButton("Logout") { _, _ ->
                    session.clear()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}