package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentAdminPlaceholderBinding

class AdminPlaceholderFragment : Fragment(R.layout.fragment_admin_placeholder) {

    private var _b: FragmentAdminPlaceholderBinding? = null
    private val b get() = _b!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentAdminPlaceholderBinding.bind(view)

        val args = requireArguments()
        b.tvTitle.text = args.getString(ARG_TITLE).orEmpty()
        b.tvSubtitle.text = args.getString(ARG_SUBTITLE).orEmpty()

        val items = args.getStringArrayList(ARG_ITEMS).orEmpty()
        b.tvItems.text = items.joinToString("\n") { "• $it" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SUBTITLE = "arg_subtitle"
        private const val ARG_ITEMS = "arg_items"

        fun newInstance(
            title: String,
            subtitle: String,
            items: ArrayList<String>
        ): AdminPlaceholderFragment {
            return AdminPlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_SUBTITLE, subtitle)
                    putStringArrayList(ARG_ITEMS, items)
                }
            }
        }
    }
}