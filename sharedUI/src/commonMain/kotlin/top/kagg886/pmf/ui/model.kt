package top.kagg886.pmf.ui

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.database.AppDatabase
import top.kagg886.pmf.database.databaseBuilder
import top.kagg886.pmf.database.util.DatabaseLogWriter
import top.kagg886.pmf.util.SnackBarAction

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 09:32
 * ================================================
 */

class RootViewModel : ViewModel(), OrbitContainerHost<RootViewModelState, RootViewModelState, RootViewModelEffect> {
    override val container: OrbitContainer<RootViewModelState, RootViewModelState, RootViewModelEffect> =
        orbitContainer(RootViewModelState.Loading) {
            val database = databaseBuilder().build()
            val logDao = database.logDao()
            Logger.addLogWriter(DatabaseLogWriter(logDao))
            reduce {
                RootViewModelState.LoadSuccess(database, SnackbarHostState())
            }
        }

    fun toast(action: SnackBarAction.() -> Unit) = intent {
        postSideEffect(RootViewModelEffect.Toast(SnackBarAction().apply(action)))
    }
}


sealed interface RootViewModelState {
    data object Loading : RootViewModelState
    data class LoadSuccess(
        val database: AppDatabase,
        val snack: SnackbarHostState
    ) : RootViewModelState
}

sealed interface RootViewModelEffect {
    data class Toast(val action: SnackBarAction) : RootViewModelEffect
}
