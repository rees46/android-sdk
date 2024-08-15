package com.personalization.utils

import java.util.EnumSet

object EnumUtils {

    inline fun <reified T : Enum<T>> getEnumsString(enums: EnumSet<T>): String {
        val string = StringBuilder()

        for (enum in enumValues<T>()) {
            if(enums.contains(enum)) {
                if(string.isNotEmpty()) {
                    string.append(',')
                }
                string.append(enum.toString())
            }
        }

        return string.toString()
    }
}
