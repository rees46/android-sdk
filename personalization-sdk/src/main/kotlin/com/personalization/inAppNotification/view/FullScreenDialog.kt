package com.personalization.inAppNotification.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.personalization.databinding.FullScreenDialogBinding

class FullScreenDialog : DialogFragment() {

    private val binding: FullScreenDialogBinding by lazy {
        FullScreenDialogBinding.inflate(layoutInflater)
    }

    private var listener: FullScreenDialogListener? = null

    fun setListener(listener: FullScreenDialogListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        with(binding) {
            Glide.with(requireContext())
                .load(arguments?.getString(IMAGE_URL_KEY).orEmpty())
                .into(backgroundImageView)

            title.text = arguments?.getString(TITLE_KEY).orEmpty()
            message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            buttonDecline.setOnClickListener {
                listener?.onNegativeButtonClick()
                dismiss()
            }
            buttonAccept.setOnClickListener {
                listener?.onPositiveButtonClick()
                dismiss()
            }
        }
    }

    interface FullScreenDialogListener {
        fun onPositiveButtonClick()
        fun onNegativeButtonClick()
    }

    companion object {
        const val TAG = "FullScreenDialog"
        const val TITLE_KEY = "TITLE_KEY"
        const val MESSAGE_KEY = "MESSAGE_KEY"
        const val IMAGE_URL_KEY = "IMAGE_URL_KEY"
        const val BUTTON_POSITIVE_TEXT_KEY = "BUTTON_POSITIVE_TEXT_KEY"
        const val BUTTON_NEGATIVE_TEXT_KEY = "BUTTON_NEGATIVE_TEXT_KEY"

        fun newInstance(
            title: String,
            message: String,
            imageUrl: String?,
            buttonPositiveText: String,
            buttonNegativeText: String
        ): FullScreenDialog {
            val dialog = FullScreenDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putString(IMAGE_URL_KEY, imageUrl)
                putString(BUTTON_POSITIVE_TEXT_KEY, buttonPositiveText)
                putString(BUTTON_NEGATIVE_TEXT_KEY, buttonNegativeText)
            }
            dialog.arguments = args
            return dialog
        }
    }
}
