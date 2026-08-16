package com.gawi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Material defaults for now. The real theme moves to :core:ui when that
 * module is born (architecture §2).
 */
@Composable
fun GawiTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
