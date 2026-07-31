package top.kagg886.pmf.ui.screen.logger

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import co.touchlab.kermit.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.entity.LogEntity
import top.kagg886.pmf.logger.Logger

@Logger
class LoggerModel(
    database: AppCommonDatabase,
) : ViewModel(), OrbitContainerHost<LoggerState, LoggerState, LoggerEffect> {
    private val logDao = database.logDao()

    override val container: OrbitContainer<LoggerState, LoggerState, LoggerEffect> =
        orbitContainer(LoggerState.Loading) {
            logger.i { "Initializing persistent log paging" }
            load()
            logger.i { "Persistent log paging is ready; setting state to LoadingSuccess" }
        }

    fun load(severity: Severity? = null) = intent {
        logger.i {
            "Refreshing persistent log paging (severity=${severity?.name ?: "all"})"
        }
        val pager = Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
            ),
        ) {
            logDao.getAll(severity?.ordinal)
        }
        reduce {
            LoggerState.LoadingSuccess(severity, pager)
        }
    }

    fun clear() = intent {
        logger.i { "User requested clearing persistent logs" }
        try {
            withContext(Dispatchers.IO) {
                logDao.clear()
            }
            logger.i { "Persistent logs cleared successfully" }
            postSideEffect(LoggerEffect.Cleared)
        } catch (e: Exception) {
            logger.e(e) { "Clearing persistent logs failed; keeping the current page state" }
            postSideEffect(LoggerEffect.ClearFailed)
        }
    }
}

sealed interface LoggerState {
    data object Loading : LoggerState

    data class LoadingSuccess(
        val filter: Severity?,
        val pager: Pager<Int, LogEntity>,
    ) : LoggerState
}

sealed interface LoggerEffect {
    data object Cleared : LoggerEffect
    data object ClearFailed : LoggerEffect
}
