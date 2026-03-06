package com.example.stardeckapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.stardeckapplication.R
import com.example.stardeckapplication.databinding.FragmentUserPortalPlaceholderBinding

class UserPortalPlaceholderFragment : Fragment(R.layout.fragment_user_portal_placeholder) {

    private var _b: FragmentUserPortalPlaceholderBinding? = null
    private val b get() = _b!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentUserPortalPlaceholderBinding.bind(view)

        val args = requireArguments()
        b.tvTitle.text = args.getString(ARG_TITLE).orEmpty()
        b.tvSubtitle.text = args.getString(ARG_SUBTITLE).orEmpty()

        val features = args.getStringArrayList(ARG_FEATURES).orEmpty()
        b.tvFeatures.text = features.joinToString("\n") { "• $it" }

        val primaryText = args.getString(ARG_PRIMARY_TEXT).orEmpty()
        val targetItemId = args.getInt(ARG_TARGET_ITEM_ID, View.NO_ID)

        if (primaryText.isBlank() || targetItemId == View.NO_ID) {
            b.btnPrimary.visibility = View.GONE
        } else {
            b.btnPrimary.text = primaryText
            b.btnPrimary.setOnClickListener {
                (activity as? UserHomeActivity)?.openTab(targetItemId)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SUBTITLE = "arg_subtitle"
        private const val ARG_FEATURES = "arg_features"
        private const val ARG_PRIMARY_TEXT = "arg_primary_text"
        private const val ARG_TARGET_ITEM_ID = "arg_target_item_id"

        fun newInstance(
            title: String,
            subtitle: String,
            features: ArrayList<String>,
            primaryText: String,
            targetItemId: Int
        ): UserPortalPlaceholderFragment {
            return UserPortalPlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_SUBTITLE, subtitle)
                    putStringArrayList(ARG_FEATURES, features)
                    putString(ARG_PRIMARY_TEXT, primaryText)
                    putInt(ARG_TARGET_ITEM_ID, targetItemId)
                }
            }
        }
    }
}