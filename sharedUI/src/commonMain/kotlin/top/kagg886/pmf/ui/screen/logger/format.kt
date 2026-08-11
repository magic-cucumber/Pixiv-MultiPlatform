package top.kagg886.pmf.ui.screen.logger

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Severity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_severity_assert
import top.kagg886.pmf.i18n.logger_severity_debug
import top.kagg886.pmf.i18n.logger_severity_error
import top.kagg886.pmf.i18n.logger_severity_info
import top.kagg886.pmf.i18n.logger_severity_unknown
import top.kagg886.pmf.i18n.logger_severity_verbose
import top.kagg886.pmf.i18n.logger_severity_warn
import kotlin.time.Instant

@Composable
internal fun severityLabel(value: Int): String = when (Severity.values().firstOrNull { it.ordinal == value }) {
    Severity.Verbose -> stringResource(Lang.string.logger_severity_verbose)
    Severity.Debug -> stringResource(Lang.string.logger_severity_debug)
    Severity.Info -> stringResource(Lang.string.logger_severity_info)
    Severity.Warn -> stringResource(Lang.string.logger_severity_warn)
    Severity.Error -> stringResource(Lang.string.logger_severity_error)
    Severity.Assert -> stringResource(Lang.string.logger_severity_assert)
    null -> stringResource(Lang.string.logger_severity_unknown)
}

internal fun formatTimestamp(timestamp: Instant): String =
    timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).toString()
