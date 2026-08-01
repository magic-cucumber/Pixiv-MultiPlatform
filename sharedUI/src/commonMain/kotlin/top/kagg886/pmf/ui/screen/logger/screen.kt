package top.kagg886.pmf.ui.screen.logger

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/8/1 19:10
 * ================================================
 */

@Serializable
data object LoggerRoute : SerializableNavKey

@Composable
fun LoggerScreen(content: @Composable () -> Unit) = content()
