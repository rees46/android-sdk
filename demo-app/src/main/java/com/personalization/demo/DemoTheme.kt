package com.personalization.demo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wraps a Compose pane in a MaterialTheme that follows the system (light in day, dark in night) plus
 * a themed [Surface] so text sits on a real background instead of a transparent window.
 *
 * The demo now supports dark mode natively — that is what stops MIUI from force-darkening the app
 * and leaving Compose text unreadable. Without a MaterialTheme, Compose defaults to a light color
 * set, so in a dark window its dark text would vanish; this makes both modes render correctly.
 *
 * Note: the SDK's stories block colors its labels from merchant settings (assuming a light backdrop),
 * so stories are placed on an explicit light surface at their call sites rather than following this
 * theme.
 */
@Composable
fun DemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            content()
        }
    }
}
