package com.personalization.features.notification.core

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

object RequestCodeGenerator {

    /**
     * Create a unique requestCode based on the hashcode of the combination of action and currentIndex
     * */
    private val requestCodeCounter = AtomicInteger(0)

    fun generateRequestCode(action: String, currentIndex: Int): Int {
        val baseCode = "${action}_${currentIndex}".hashCode()
        val uniqueCode = if (baseCode != Int.MIN_VALUE) abs(baseCode) else 0

        return requestCodeCounter.incrementAndGet() + uniqueCode
    }
}
