package com.personalization.inAppNotification.view.component.dialog.fullScreen

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.personalization.databinding.FullScreenDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer
import com.personalization.inAppNotification.view.component.dialog.BaseInAppDialog
import com.personalization.inAppNotification.view.component.dialog.fullScreen.container.FullScreenDialogViewContainer
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.IMAGE_URL_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.MESSAGE_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.TITLE_KEY

const val FULL_SCREEN_DIALOG_TAG = "FullScreenDialog"

class FullScreenDialog : BaseInAppDialog() {

    private var _binding: FullScreenDialogBinding? = null
    private val binding get() = _binding!!

    override val container: InAppViewContainer by lazy {
        FullScreenDialogViewContainer(binding)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FullScreenDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
        }
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
            buttonDeclineColor: Int?
        ): FullScreenDialog {
            val dialog = FullScreenDialog()
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
