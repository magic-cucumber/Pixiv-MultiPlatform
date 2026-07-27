package top.kagg886.pmf

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.kagg886.pmf.fronted.ApplicationGraph
import top.kagg886.pmf.fronted.welcome.WelcomeRoute
import top.kagg886.pmf.util.nav3.NavDisplay
import top.kagg886.pmf.util.nav3.rememberNavController

@Composable
fun App(onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}) {
    onThemeChanged(isSystemInDarkTheme())

    val controller = rememberNavController(
        graph = ApplicationGraph,
        startDestination = WelcomeRoute,
        serializersModule = ApplicationNavSerializerModule,
    )
    MaterialTheme {
        NavDisplay(
            controller = controller,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxSize()
        )
    }
}
