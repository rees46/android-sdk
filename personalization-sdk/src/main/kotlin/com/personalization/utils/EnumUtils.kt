package com.personalization.utils

import java.util.EnumSet

object EnumUtils {

    inline fun <reified T : Enum<T>> getEnumsString(enums: EnumSet<T>): String {
        return enums.joinToString(",") { it.toString() }
    }

    infix fun <T : Enum<T>> EnumSet<T>.and(other: T): EnumSet<T> {
        this.add(other)
        return this
    }
}
