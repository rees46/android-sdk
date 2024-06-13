package com.personalizatio

fun interface OnMessageListener {
    fun onMessage(data: Map<String, String>)
}
