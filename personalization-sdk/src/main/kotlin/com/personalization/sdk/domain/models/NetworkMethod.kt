package com.personalization.sdk.domain.models

sealed class NetworkMethod(val type: String, val method: String) {

    class POST(method: String) : NetworkMethod(type = "POST", method = method)

    class GET(method: String) : NetworkMethod(type = "GET", method = method)
}
