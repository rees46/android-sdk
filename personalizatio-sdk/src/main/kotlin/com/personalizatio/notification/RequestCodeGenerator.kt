package com.personalizatio.notification

import kotlin.math.abs

object RequestCodeGenerator {

    /**
     * Create a unique requestCode based on the hashcode of the combination of action and currentIndex
     * */
    fun generateRequestCode(action: String, currentIndex: Int): Int {
        val uniqueKey = "${action}_${currentIndex}".hashCode()
        return if (uniqueKey != Int.MIN_VALUE) abs(uniqueKey) else 0
    }
}
