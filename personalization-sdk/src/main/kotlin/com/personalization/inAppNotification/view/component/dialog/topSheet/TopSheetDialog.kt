@file:Suppress("NewApi")

package com.personalization.inAppNotification.view.component.dialog.topSheet

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.personalization.databinding.TopSheetDialogBinding
import com.personalization.inAppNotification.view.component.container.InAppViewContainer
import com.personalization.inAppNotification.view.component.dialog.BaseInAppDialog
import com.personalization.inAppNotification.view.component.dialog.topSheet.container.TopSheetDialogViewContainer
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_DECLINE_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_COLOR_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.BUTTON_CONFIRM_TEXT_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.IMAGE_URL_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.MESSAGE_KEY
import com.personalization.inAppNotification.view.component.utils.InAppConsts.TITLE_KEY

const val TOP_SHEET_DIALOG = "TopSheetDialog"

class TopSheetDialog : BaseInAppDialog() {

    private var _binding: TopSheetDialogBinding? = null
    private val binding get() = _binding!!

    override val container: InAppViewContainer by lazy {
        TopSheetDialogViewContainer(binding)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TopSheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
        }
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
        ): TopSheetDialog {
            val dialog = TopSheetDialog()
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
