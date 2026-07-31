package top.kagg886.pmf.ui.screen.logger

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.create
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_clear_failed
import top.kagg886.pmf.i18n.logger_clear_success
import top.kagg886.pmf.util.databasePath
import top.kagg886.pmf.util.nav3.SerializableNavKey

@kotlinx.serialization.Serializable
data object LoggerRoute : SerializableNavKey

@Composable
fun LoggerScreen() {
    val model = viewModel {
        LoggerModel(AppCommonDatabase.create(databasePath / "common.db"))
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val clearSuccessMessage = stringResource(Lang.string.logger_clear_success)
    val clearFailedMessage = stringResource(Lang.string.logger_clear_failed)
    model.collectSideEffect { effect ->
        snackbarHostState.showSnackbar(
            when (effect) {
                LoggerEffect.Cleared -> clearSuccessMessage
                LoggerEffect.ClearFailed -> clearFailedMessage
            }
        )
    }
}
