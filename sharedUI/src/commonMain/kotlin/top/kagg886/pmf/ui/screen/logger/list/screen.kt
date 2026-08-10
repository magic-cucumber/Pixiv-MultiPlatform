package top.kagg886.pmf.ui.screen.logger.list

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.compose.collectAsLazyPagingItems
import co.touchlab.kermit.Severity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.create
import top.kagg886.pmf.database.common.entity.LogEntity
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_clear_failed
import top.kagg886.pmf.i18n.logger_clear_success
import top.kagg886.pmf.i18n.logger_clear
import top.kagg886.pmf.i18n.logger_empty
import top.kagg886.pmf.i18n.logger_load_failed
import top.kagg886.pmf.i18n.logger_loading
import top.kagg886.pmf.i18n.logger_retry
import top.kagg886.pmf.i18n.logger_title
import top.kagg886.pmf.i18n.logger_entry_summary
import top.kagg886.pmf.i18n.logger_list_collapse
import top.kagg886.pmf.i18n.logger_list_expand
import top.kagg886.pmf.i18n.logger_severity_assert
import top.kagg886.pmf.i18n.logger_severity_debug
import top.kagg886.pmf.i18n.logger_severity_error
import top.kagg886.pmf.i18n.logger_severity_info
import top.kagg886.pmf.i18n.logger_severity_unknown
import top.kagg886.pmf.i18n.logger_severity_verbose
import top.kagg886.pmf.i18n.logger_severity_warn
import top.kagg886.pmf.ui.component.ExpandableText
import top.kagg886.pmf.ui.screen.logger.detail.LoggerDetailRoute
import top.kagg886.pmf.util.databasePath
import top.kagg886.pmf.util.nav3.SerializableNavKey
import kotlin.time.Instant

@Serializable
data object LoggerListRoute : SerializableNavKey

@Composable
fun LoggerListScreen() {
    val model = viewModel {
        LoggerListModel(AppCommonDatabase.create(databasePath / "common.db"))
    }
    val nav = LocalNavController.current
    val state by model.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Lang.string.logger_title)) },
                actions = {
                    TextButton(onClick = model::clear) {
                        Text(stringResource(Lang.string.logger_clear))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        when (val currentState = state) {
            LoggerState.Loading -> LoadingContent(contentPadding)
            is LoggerState.LoadingSuccess -> LoggerListContent(
                pager = currentState.pager,
                contentPadding = contentPadding,
                onLogClick = { log -> nav.navigate(LoggerDetailRoute(log)) },
            )
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(Lang.string.logger_loading))
        }
    }
}

@Composable
private fun LoggerListContent(
    pager: Pager<Int, LogEntity>,
    contentPadding: PaddingValues,
    onLogClick: (LogEntity) -> Unit,
) {
    val logs = pager.flow.collectAsLazyPagingItems()

    when (logs.itemCount) {
        0 if logs.loadState.refresh is LoadState.Loading -> LoadingContent(contentPadding)
        0 if logs.loadState.refresh is LoadState.Error -> ErrorContent(
            contentPadding = contentPadding,
            onRetry = logs::retry,
        )
        0 if logs.loadState.refresh is LoadState.NotLoading -> EmptyContent(contentPadding)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(
                count = logs.itemCount,
                key = { index -> logs[index]?.id ?: "log-placeholder-$index" },
            ) { index ->
                val log = logs[index]
                if (log == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LogListItem(log = log, onClick = { onLogClick(log) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogListItem(log: LogEntity, onClick: () -> Unit) {
    val state = loggerItemState(log)
    var isExpanded by rememberSaveable(log.id) { mutableStateOf(false) }
    val content = remember(log.message, log.stacktrace) {
        buildString {
            append(log.message)
            log.stacktrace
                ?.takeIf(String::isNotBlank)
                ?.let { stacktrace ->
                    appendLine()
                    append(stacktrace)
                }
        }
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .clickable(onClick = onClick),
        leadingContent = {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = state.icon,
                contentDescription = null,
                tint = state.iconColor,
            )
        },
        headlineContent = {
            Text(
                text = state.identifier,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overlineContent = {
            Text(
                text = stringResource(
                    Lang.string.logger_entry_summary,
                    severityLabel(log.severity),
                    formatTimestamp(log.timestamp),
                ),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            ExpandableText(
                text = content,
                expandText = stringResource(Lang.string.logger_list_expand),
                collapseText = stringResource(Lang.string.logger_list_collapse),
                collapsedMaxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                isExpanded = isExpanded,
                onExpandChange = { isExpanded = it },
            )
        },
    )
}

@Preview
@Composable
private fun LogListItemPreview() {
    MaterialTheme {
        LogListItem(
            log = LogEntity(
                tag = "top.kagg886.pmf.ui.screen.main.MainViewModel",
                severity = Severity.Error.ordinal,
                message = "Loading failed; setting state to Error",
                timestamp = Instant.parse("2026-08-01T12:00:00Z"),
                stacktrace = "IllegalStateException: response body was empty\n\tat Repository.load(Repository.kt:42)",
            ),
            onClick = {},
        )
    }
}

private data class LoggerItemState(
    val icon: ImageVector,
    val iconColor: Color,
    val identifier: String,
)

@Composable
private fun loggerItemState(log: LogEntity): LoggerItemState {
    val colors = MaterialTheme.colorScheme
    return when (Severity.values().firstOrNull { it.ordinal == log.severity }) {
        Severity.Verbose -> LoggerItemState(Icons.Outlined.Visibility, colors.secondary, log.tag)
        Severity.Debug -> LoggerItemState(Icons.Outlined.BugReport, colors.tertiary, log.tag)
        Severity.Info -> LoggerItemState(Icons.Outlined.Info, colors.primary, log.tag)
        Severity.Warn -> LoggerItemState(Icons.Outlined.Warning, colors.error.copy(alpha = 0.8f), log.tag)
        Severity.Error -> LoggerItemState(Icons.Outlined.Error, colors.error, log.tag)
        Severity.Assert -> LoggerItemState(Icons.Outlined.PriorityHigh, colors.error, log.tag)
        null -> LoggerItemState(Icons.Outlined.Info, colors.onSurfaceVariant, log.tag)
    }
}

@Composable
private fun ErrorContent(contentPadding: PaddingValues, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Lang.string.logger_load_failed))
            TextButton(onClick = onRetry) {
                Text(stringResource(Lang.string.logger_retry))
            }
        }
    }
}

@Composable
private fun EmptyContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Lang.string.logger_empty))
    }
}

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
