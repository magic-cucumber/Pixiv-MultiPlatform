package top.kagg886.pmf.ui

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.database.AppDatabase
import top.kagg886.pmf.database.databaseBuilder
import top.kagg886.pmf.database.util.DatabaseLogWriter

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
                RootViewModelState.LoadSuccess(database)
            }
        }
}


sealed interface RootViewModelState {
    data object Loading : RootViewModelState
    data class LoadSuccess(
        val database: AppDatabase,
    ) : RootViewModelState
}

sealed interface RootViewModelEffect {
}
