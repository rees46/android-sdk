package com.personalization.push

import android.content.Context
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.push.HmsMessaging
import com.personalization.PushProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * HMS token source. Requires the host app to provide `agconnect-services.json` and apply the
 * `com.huawei.agconnect` plugin.
 *
 * On many devices `getToken` returns an empty token and the real token is delivered
 * asynchronously through [com.personalization.HmsMessagingService.onNewToken]; this source
 * therefore completes with whatever `getToken` returns (often empty) and relies on the
 * service callback for the actual value. `getToken` must not run on the main thread.
 */
class HmsTokenSource : PushTokenSource {

    override val provider: PushProvider = PushProvider.HMS

    override fun isAvailable(context: Context): Boolean = try {
        HuaweiApiAvailability.getInstance()
            .isHuaweiMobileServicesAvailable(context) == com.huawei.hms.api.ConnectionResult.SUCCESS
    } catch (e: Exception) {
        false
    }

    override fun fetchToken(context: Context, callback: (Result<String?>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appId = AGConnectOptionsBuilder().build(context).getString("client/app_id")
                val token = HmsInstanceId.getInstance(context)
                    .getToken(appId, HmsMessaging.DEFAULT_TOKEN_SCOPE)
                callback(Result.success(token))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}
