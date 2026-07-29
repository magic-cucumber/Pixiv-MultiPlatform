package top.kagg886.pmf.fronted.main.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/29 15:22
 * ================================================
 */

@Serializable
data object HomeRoute: SerializableNavKey


@Composable
fun HomeScreen() {
    Text("login success")
}
