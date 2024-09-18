package com.personalization.inAppNotification.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.personalization.databinding.AlertDialogBinding

class AlertDialog : DialogFragment() {

    private var _binding: AlertDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AlertDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        with(binding) {
            title.text = arguments?.getString(TITLE_KEY).orEmpty()
            message.text = arguments?.getString(MESSAGE_KEY).orEmpty()
            button.setOnClickListener {
                dismiss()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val TAG = "AlertDialog"
        const val TITLE_KEY = "title"
        const val MESSAGE_KEY = "message"

        fun newInstance(title: String, message: String): AlertDialog {
            val dialog = AlertDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
            }
            dialog.arguments = args
            return dialog
        }
    }
}