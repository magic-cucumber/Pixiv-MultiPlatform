package top.kagg886.pmf.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/13 16:58
 * ================================================
 */

@Composable
fun trace(tag: String, content: @Composable LoggerScope.() -> Unit) {
    val logger: LoggerScope = remember(tag) { LoggerScopeImpl(tag) }
    content.invoke(logger)
}

@Composable
fun TraceEffect(vararg keys: Any?, content: suspend LoggerScope.() -> Unit) {
    check(keys.isNotEmpty()) { "Keys must not be empty." }
    check(keys[0] is String) { "Key's first element must be a string tag." }

    val logger: LoggerScope = remember(keys[0]) { LoggerScopeImpl(keys[0].toString()) }
    LaunchedEffect(*keys) {
        content.invoke(logger)
    }
}

interface LoggerScope {
    fun log(severity: Severity, message: String, throwable: Throwable? = null)
}

fun LoggerScope.v(message: String) =
    log(severity = Severity.Verbose, message)

fun LoggerScope.d(message: String) =
    log(severity = Severity.Debug, message)

fun LoggerScope.i(message: String) =
    log(severity = Severity.Info, message)

fun LoggerScope.w(message: String) =
    log(severity = Severity.Warn, message)

fun LoggerScope.e(message: String) =
    log(severity = Severity.Error, message)

fun LoggerScope.v(throwable: Throwable? = null, message: String) =
    log(severity = Severity.Verbose, message, throwable)

fun LoggerScope.d(throwable: Throwable? = null, message: String) =
    log(severity = Severity.Debug, message, throwable)

fun LoggerScope.i(throwable: Throwable? = null, message: String) =
    log(severity = Severity.Info, message, throwable)

fun LoggerScope.w(throwable: Throwable? = null, message: String) =
    log(severity = Severity.Warn, message, throwable)

fun LoggerScope.e(throwable: Throwable? = null, message: String) =
    log(severity = Severity.Error, message, throwable)

fun LoggerScope.v(throwable: Throwable? = null, message: () -> String) =
    log(severity = Severity.Verbose, message(), throwable)

fun LoggerScope.d(throwable: Throwable? = null, message: () -> String) =
    log(severity = Severity.Debug, message(), throwable)

fun LoggerScope.i(throwable: Throwable? = null, message: () -> String) =
    log(severity = Severity.Info, message(), throwable)

fun LoggerScope.w(throwable: Throwable? = null, message: () -> String) =
    log(severity = Severity.Warn, message(), throwable)

fun LoggerScope.e(throwable: Throwable? = null, message: () -> String) =
    log(severity = Severity.Error, message(), throwable)

private class LoggerScopeImpl(private val tag: String) : LoggerScope {
    override fun log(severity: Severity, message: String, throwable: Throwable?) {
        Logger.log(severity, tag, throwable, message)
    }
}
