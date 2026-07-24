package top.kagg886.pmf

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay

@Composable
fun App(onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}) {
    NavDisplay(
        backStack = listOf<NavKey>(),
        entryProvider = { key ->
            when (key) {
                is MainScreen -> NavEntry(key) { Text("Product List") }
                else -> error("Unknown screen key $key")
            }
        }
    )
}


data class MainScreen(val i: Int) : NavKey
