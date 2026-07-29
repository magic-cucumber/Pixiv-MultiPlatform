package top.kagg886.pmf.fronted.main

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:19
 * ================================================
 */

@Serializable
data object MainRoute : SerializableNavKey

@Composable
fun MainScreen(content:@Composable () -> Unit) {
    content()
}
