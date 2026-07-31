package top.kagg886.pmf

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import top.kagg886.pmf.screen.ApplicationGraph
import top.kagg886.pmf.screen.welcome.WelcomeRoute
import top.kagg886.pmf.util.nav3.NavController
import top.kagg886.pmf.util.nav3.NavDisplay
import top.kagg886.pmf.util.nav3.SerializableNavKey
import top.kagg886.pmf.util.nav3.rememberNavController


val LocalNavController = staticCompositionLocalOf<NavController<SerializableNavKey>> {
    error("LocalNavController not provided")
}

@Composable
fun App(onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}) {
    onThemeChanged(isSystemInDarkTheme())

    val controller = rememberNavController(
        graph = ApplicationGraph,
        startDestination = WelcomeRoute,
        serializersModule = ApplicationNavSerializerModule,
    )

    CompositionLocalProvider(LocalNavController provides controller) {
        MaterialTheme {
            NavDisplay(
                controller = controller,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxSize()
            )
        }
    }
}
