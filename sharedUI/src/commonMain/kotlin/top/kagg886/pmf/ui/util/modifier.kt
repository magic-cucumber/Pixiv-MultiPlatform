package top.kagg886.pmf.ui.util

import androidx.compose.ui.Modifier

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/11 09:31
 * ================================================
 */

fun Modifier.applyIf(condition: Boolean,block: Modifier) = if (condition) this then block else this
