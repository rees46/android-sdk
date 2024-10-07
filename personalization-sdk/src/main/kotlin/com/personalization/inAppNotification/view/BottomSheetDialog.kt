@file:Suppress("NewApi")

package com.personalization.inAppNotification.view

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.personalization.databinding.BottomSheetDialogBinding

class BottomSheetDialog : BottomSheetDialogFragment() {

    private var listener: BottomSheetDialogListener? = null

    fun setListener(listener: BottomSheetDialogListener) {
        this.listener = listener
    }

    private var _binding: BottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        (view.parent as View).apply {
            backgroundTintMode = PorterDuff.Mode.CLEAR
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
        }

        with(binding) {
            title.text = arguments?.getString(TITLE_KEY).orEmpty()
            message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            buttonAccept.text = arguments?.getString(BUTTON_POSITIVE_TEXT_KEY).orEmpty()
            buttonDecline.text = arguments?.getString(BUTTON_NEGATIVE_TEXT_KEY).orEmpty()

            buttonAccept.setOnClickListener {
                listener?.onPositiveButtonClick()
                dismiss()
            }
            buttonDecline.setOnClickListener {
                listener?.onNegativeButtonClick()
                dismiss()
            }
        }
    }

    interface BottomSheetDialogListener {
        fun onPositiveButtonClick()
        fun onNegativeButtonClick()
    }

    companion object {
        const val TAG = "BottomSheetDialog"
        const val TITLE_KEY = "TITLE_KEY"
        const val MESSAGE_KEY = "MESSAGE_KEY"
        const val BUTTON_POSITIVE_TEXT_KEY = "BUTTON_POSITIVE_TEXT_KEY"
        const val BUTTON_NEGATIVE_TEXT_KEY = "BUTTON_NEGATIVE_TEXT_KEY"

        fun newInstance(
            title: String,
            message: String,
            buttonPositiveText: String,
            buttonNegativeText: String
        ): BottomSheetDialog {
            val dialog = BottomSheetDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putString(BUTTON_POSITIVE_TEXT_KEY, buttonPositiveText)
                putString(BUTTON_NEGATIVE_TEXT_KEY, buttonNegativeText)
            }
            dialog.arguments = args
            return dialog
        }
    }
}
