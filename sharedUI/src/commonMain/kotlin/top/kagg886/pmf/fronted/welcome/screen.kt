package top.kagg886.pmf.fronted.welcome

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import top.kagg886.pmf.util.nav3.SerializableNavKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 13:41
 * ================================================
 */

@Serializable
data object WelcomeRoute: SerializableNavKey


@Composable
fun WelcomeScreen() {
    Text("Welcome!")
}
