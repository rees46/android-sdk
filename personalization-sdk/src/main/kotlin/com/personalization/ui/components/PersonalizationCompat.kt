package com.personalization.ui.components

import android.os.Build
import android.widget.TextView

/**
 * Трекинг текста в em. `TextView.letterSpacing` есть только с API 21, а SDK держит minSdk 19:
 * там вызов упал бы с NoSuchMethodError, поэтому на старых версиях трекинг не ставится —
 * это доли пикселя, вёрстка от них не зависит.
 */
internal fun TextView.setLetterSpacingCompat(em: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) letterSpacing = em
}
