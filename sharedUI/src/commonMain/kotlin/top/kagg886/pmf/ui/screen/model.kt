package top.kagg886.pmf.ui.screen

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger as KermitLogger
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.create
import top.kagg886.pmf.database.util.DatabaseLogWriter
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.util.databasePath

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/27 09:32
 * ================================================
 */

@Logger
class RootViewModel : ViewModel(), OrbitContainerHost<RootViewModelState, RootViewModelState, RootViewModelEffect> {
    override val container: OrbitContainer<RootViewModelState, RootViewModelState, RootViewModelEffect> =
        orbitContainer(RootViewModelState.Loading) {
            logger.i { "Starting root page model initialization" }
            try {
                logger.d { "Preparing to create the common database and install the persistent log writer" }
                val database = AppCommonDatabase.create(databasePath / "common.db")
                val logDao = database.logDao()
                KermitLogger.addLogWriter(DatabaseLogWriter(logDao))
                logger.i { "Persistent log writer installed successfully; setting state to LoadSuccess" }
                reduce {
                    RootViewModelState.LoadSuccess
                }
            } catch (e: Exception) {
                logger.e(e) { "Root page model initialization failed; setting state to Error" }
                reduce {
                    RootViewModelState.Error
                }
            }
        }
}


sealed interface RootViewModelState {
    data object Loading : RootViewModelState
    data object LoadSuccess : RootViewModelState
    data object Error : RootViewModelState
}

sealed interface RootViewModelEffect {
}
