package com.personalizatio.notification

import java.util.concurrent.atomic.AtomicInteger

object RequestCodeGenerator {
    private val counter = AtomicInteger(0)

    fun getNextRequestCode(): Int {
        return counter.getAndIncrement()
    }
}