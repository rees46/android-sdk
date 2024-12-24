package com.personalization.sdk.data.repositories.gaid

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient

class GaIdDataSource {
    internal fun fetchGaId(context: Context): String =
        AdvertisingIdClient.getAdvertisingIdInfo(context).id
}