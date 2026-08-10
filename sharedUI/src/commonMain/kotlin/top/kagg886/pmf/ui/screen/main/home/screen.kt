package top.kagg886.pmf.ui.screen.main.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.home_login_success
import top.kagg886.pmf.ui.screen.logger.LoggerRoute
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
    Text(stringResource(Lang.string.home_login_success))
    LocalNavController.current.navigate(LoggerRoute)
}
