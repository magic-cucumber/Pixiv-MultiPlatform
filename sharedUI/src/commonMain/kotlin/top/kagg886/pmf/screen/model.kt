package top.kagg886.pmf.screen

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.create
import top.kagg886.pmf.database.util.DatabaseLogWriter
import top.kagg886.pmf.util.databasePath

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 09:32
 * ================================================
 */

class RootViewModel : ViewModel(), OrbitContainerHost<RootViewModelState, RootViewModelState, RootViewModelEffect> {
    override val container: OrbitContainer<RootViewModelState, RootViewModelState, RootViewModelEffect> =
        orbitContainer(RootViewModelState.Loading) {
            val database = AppCommonDatabase.create(databasePath / "common.db")
            val logDao = database.logDao()
            Logger.addLogWriter(DatabaseLogWriter(logDao))
            reduce {
                RootViewModelState.LoadSuccess
            }
        }
}


sealed interface RootViewModelState {
    data object Loading : RootViewModelState
    data object LoadSuccess : RootViewModelState
}

sealed interface RootViewModelEffect {
}
