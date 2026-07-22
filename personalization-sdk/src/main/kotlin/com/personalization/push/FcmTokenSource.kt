package com.personalization.push

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.personalization.PushProvider

/**
 * FCM token source. Requires the host app to provide `google-services.json` and apply the
 * `google-services` plugin so that `FirebaseApp` is initialized.
 */
class FcmTokenSource : PushTokenSource {

    override val provider: PushProvider = PushProvider.FCM

    override fun isAvailable(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (e: Exception) {
        false
    }

    override fun fetchToken(context: Context, callback: (Result<String?>) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(Result.success(task.result))
                } else {
                    callback(
                        Result.failure(
                            task.exception ?: IllegalStateException("FCM token task failed")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
}
