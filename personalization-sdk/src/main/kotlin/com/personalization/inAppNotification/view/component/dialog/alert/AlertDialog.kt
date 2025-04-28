package com.personalization.inAppNotification.view.component.dialog.alert

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.personalization.databinding.AlertDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer
import com.personalization.inAppNotification.view.component.dialog.BaseInAppDialog
import com.personalization.inAppNotification.view.component.dialog.alert.container.AlertDialogViewContainer
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.IMAGE_URL_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.MESSAGE_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.TITLE_KEY

const val ALERT_DIALOG_TAG = "AlertDialog"

class AlertDialog : BaseInAppDialog() {

    private var _binding: AlertDialogBinding? = null
    private val binding get() = _binding!!

    override val container: InAppViewContainer by lazy {
        AlertDialogViewContainer(binding)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setGravity(Gravity.CENTER)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AlertDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    companion object {
        fun newInstance(
            title: String,
            message: String,
            imageUrl: String?,
            buttonConfirmText: String?,
            buttonDeclineText: String?,
            buttonConfirmColor: Int?,
            buttonDeclineColor: Int?,
        ): AlertDialog {
            val dialog = AlertDialog()
            val args = Bundle().apply {
                putString(TITLE_KEY, title)
                putString(MESSAGE_KEY, message)
                putString(IMAGE_URL_KEY, imageUrl)
                putString(BUTTON_CONFIRM_TEXT_KEY, buttonConfirmText)
                putString(BUTTON_DECLINE_TEXT_KEY, buttonDeclineText)
                buttonConfirmColor?.let { putInt(BUTTON_CONFIRM_COLOR_KEY, it) }
                buttonDeclineColor?.let { putInt(BUTTON_DECLINE_COLOR_KEY, it) }
            }
            dialog.arguments = args
            return dialog
        }
    }
}
