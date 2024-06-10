package com.personalizatio.api

sealed class ApiMethod(val type: String, val method: String) {

    class POST(method: String): ApiMethod(type = "POST", method = method)

    class GET(method: String): ApiMethod(type = "GET", method = method)
}
