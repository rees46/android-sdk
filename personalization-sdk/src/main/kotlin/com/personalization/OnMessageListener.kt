package com.personalization

fun interface OnMessageListener {
    fun onMessage(data: Map<String, String>)
}
