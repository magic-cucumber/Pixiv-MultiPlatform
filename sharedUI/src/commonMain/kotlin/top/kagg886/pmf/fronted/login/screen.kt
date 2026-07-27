package top.kagg886.pmf.fronted.login

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27
 * ================================================
 */

@Serializable
data object LoginRoute : SerializableNavKey

@Composable
fun LoginScreen() {
    Text("Login")
}
