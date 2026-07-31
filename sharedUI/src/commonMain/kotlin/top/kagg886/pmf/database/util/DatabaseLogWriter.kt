package top.kagg886.pmf.database.util

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.kagg886.pmf.database.common.dao.LogDao
import top.kagg886.pmf.database.common.entity.LogEntity
import kotlin.time.Clock

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/31 15:15
 * ================================================
 */
class DatabaseLogWriter(
    private val logDao: LogDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        scope.launch {
            logDao.insert(
                LogEntity(
                    tag = tag,
                    severity = severity.ordinal,
                    message = message,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    stacktrace = throwable?.stackTraceToString(),
                )
            )
        }
    }
}
