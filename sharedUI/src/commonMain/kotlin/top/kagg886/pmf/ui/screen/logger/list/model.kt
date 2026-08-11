package top.kagg886.pmf.ui.screen.logger.list

import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CancellationException
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
class LoggerListModel(
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
        val currentState = state as? LoggerState.LoadingSuccess ?: return@intent
        if (currentState.isClearing) {
            logger.v { "Ignoring duplicate clear request because log clearing is already in progress" }
            return@intent
        }
        logger.i { "User requested clearing persistent logs" }
        logger.i { "Setting logger list state to clearing" }
        reduce { currentState.copy(isClearing = true) }
        try {
            withContext(Dispatchers.IO) {
                logDao.clear()
            }
            logger.i { "Persistent logs cleared successfully; posting Cleared effect" }
            postSideEffect(LoggerEffect.Cleared)
        } catch (e: CancellationException) {
            logger.i { "Clearing persistent logs was cancelled" }
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Clearing persistent logs failed; posting ClearFailed effect" }
            postSideEffect(LoggerEffect.ClearFailed)
        } finally {
            reduce {
                (state as? LoggerState.LoadingSuccess)?.copy(isClearing = false) ?: state
            }
            logger.i { "Log clearing finished; setting logger list state to not clearing" }
        }
    }

    fun openDetail(log: LogEntity) = intent {
        logger.i {
            "User selected a persistent log entry (id=${log.id}, severity=${log.severity}); posting OpenDetail effect"
        }
        postSideEffect(LoggerEffect.OpenDetail(log))
    }
}

sealed interface LoggerState {
    data object Loading : LoggerState

    data class LoadingSuccess(
        val filter: Severity?,
        val pager: Pager<Int, LogEntity>,
        val isClearing: Boolean = false,
    ) : LoggerState
}

sealed interface LoggerEffect {
    data object Cleared : LoggerEffect
    data object ClearFailed : LoggerEffect
    data class OpenDetail(val log: LogEntity) : LoggerEffect
}
