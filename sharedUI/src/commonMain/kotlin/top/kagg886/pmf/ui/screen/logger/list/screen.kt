package top.kagg886.pmf.ui.screen.logger.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import co.touchlab.kermit.Severity
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.common.AppCommonDatabase
import top.kagg886.pmf.database.common.create
import top.kagg886.pmf.database.common.entity.LogEntity
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_clear
import top.kagg886.pmf.i18n.logger_clear_failed
import top.kagg886.pmf.i18n.logger_clear_success
import top.kagg886.pmf.i18n.logger_empty
import top.kagg886.pmf.i18n.logger_entry_summary
import top.kagg886.pmf.i18n.logger_list_back
import top.kagg886.pmf.i18n.logger_list_collapse
import top.kagg886.pmf.i18n.logger_list_expand
import top.kagg886.pmf.i18n.logger_load_failed
import top.kagg886.pmf.i18n.logger_loading
import top.kagg886.pmf.i18n.logger_retry
import top.kagg886.pmf.i18n.logger_title
import top.kagg886.pmf.ui.component.EmptyScreen
import top.kagg886.pmf.ui.component.ExpandableText
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.screen.logger.formatTimestamp
import top.kagg886.pmf.ui.screen.logger.detail.LoggerDetailRoute
import top.kagg886.pmf.ui.screen.logger.severityLabel
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
        when (effect) {
            LoggerEffect.Cleared -> snackbarHostState.showSnackbar(clearSuccessMessage)
            LoggerEffect.ClearFailed -> snackbarHostState.showSnackbar(clearFailedMessage)
            is LoggerEffect.OpenDetail -> nav.navigate(LoggerDetailRoute(effect.log))
        }
    }

    when (val currentState = state) {
        LoggerState.Loading -> LoggerListContent(
            screenState = LoggerListScreenState.Loading,
            isClearing = false,
            clearEnabled = false,
            snackbarHostState = snackbarHostState,
            onBack = nav::popBackStack,
            onClear = model::clear,
            onRetry = { model.load() },
            onLogClick = model::openDetail,
        )

        is LoggerState.LoadingSuccess -> {
            val logs = currentState.pager.flow.collectAsLazyPagingItems()
            val screenState = when {
                logs.itemCount > 0 -> LoggerListScreenState.Success
                logs.loadState.refresh is LoadState.Loading -> LoggerListScreenState.Loading
                logs.loadState.refresh is LoadState.Error -> LoggerListScreenState.Error
                else -> LoggerListScreenState.Empty
            }
            LoggerListContent(
                screenState = screenState,
                isClearing = currentState.isClearing,
                clearEnabled = true,
                entries = LoggerEntriesState(
                    itemCount = logs.itemCount,
                    itemKey = { index -> logs.peek(index)?.id ?: "log-placeholder-$index" },
                    itemAt = { index -> logs[index] },
                    isRefreshing = logs.loadState.refresh is LoadState.Loading,
                ),
                snackbarHostState = snackbarHostState,
                onBack = nav::popBackStack,
                onClear = model::clear,
                onRetry = logs::retry,
                onLogClick = model::openDetail,
            )
        }
    }
}

private enum class LoggerListScreenState {
    Loading,
    Success,
    Error,
    Empty,
}

private data class LoggerEntriesState(
    val itemCount: Int,
    val itemKey: (Int) -> Any,
    val itemAt: (Int) -> LogEntity?,
    val isRefreshing: Boolean,
)

@Composable
private fun LoggerListContent(
    screenState: LoggerListScreenState,
    isClearing: Boolean,
    clearEnabled: Boolean,
    entries: LoggerEntriesState? = null,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onLogClick: (LogEntity) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Lang.string.logger_title)) },
                actions = {
                    TextButton(
                        onClick = onClear,
                        enabled = clearEnabled && !isClearing,
                    ) {
                        AnimatedContent(
                            targetState = isClearing,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "clear logs progress",
                        ) { clearing ->
                            if (clearing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(Lang.string.logger_clear))
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Lang.string.logger_list_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        AnimatedContent(
            targetState = screenState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "logger list state",
        ) { targetState ->
            when (targetState) {
                LoggerListScreenState.Loading -> LoadingContent()
                LoggerListScreenState.Success -> LoggerEntriesContent(
                    state = requireNotNull(entries),
                    onLogClick = onLogClick,
                )
                LoggerListScreenState.Error -> ErrorContent(onRetry)
                LoggerListScreenState.Empty -> EmptyContent()
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
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
private fun LoggerEntriesContent(
    state: LoggerEntriesState,
    onLogClick: (LogEntity) -> Unit,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            items(
                count = state.itemCount,
                key = state.itemKey,
            ) { index ->
                val log = state.itemAt(index)
                if (log == null) {
                    LogItemPlaceholder()
                } else {
                    LogListItem(log = log, onClick = { onLogClick(log) })
                    HorizontalDivider()
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )

        AnimatedVisibility(
            visible = state.isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LogItemPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
private fun ErrorContent(onRetry: () -> Unit) {
    EmptyScreen(
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        },
        title = { Text(stringResource(Lang.string.logger_load_failed)) },
        actions = {
            Button(onClick = onRetry) {
                Text(stringResource(Lang.string.logger_retry))
            }
        },
    )
}

@Composable
private fun EmptyContent() {
    EmptyScreen(
        icon = {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        },
        title = { Text(stringResource(Lang.string.logger_empty)) },
    )
}

@Preview(name = "Loading")
@Composable
private fun LoggerListLoadingPreview() {
    MaterialTheme {
        LoggerListContent(
            screenState = LoggerListScreenState.Loading,
            isClearing = false,
            clearEnabled = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onClear = {},
            onRetry = {},
            onLogClick = {},
        )
    }
}

@Preview(name = "Success")
@Composable
private fun LoggerListSuccessPreview() {
    val logs = previewLogs()
    MaterialTheme {
        LoggerListContent(
            screenState = LoggerListScreenState.Success,
            isClearing = false,
            clearEnabled = true,
            entries = LoggerEntriesState(
                itemCount = logs.size,
                itemKey = { index -> logs[index].id },
                itemAt = logs::get,
                isRefreshing = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onClear = {},
            onRetry = {},
            onLogClick = {},
        )
    }
}

@Preview(name = "Error")
@Composable
private fun LoggerListErrorPreview() {
    MaterialTheme {
        LoggerListContent(
            screenState = LoggerListScreenState.Error,
            isClearing = false,
            clearEnabled = true,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onClear = {},
            onRetry = {},
            onLogClick = {},
        )
    }
}

@Preview(name = "Empty")
@Composable
private fun LoggerListEmptyPreview() {
    MaterialTheme {
        LoggerListContent(
            screenState = LoggerListScreenState.Empty,
            isClearing = false,
            clearEnabled = true,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onClear = {},
            onRetry = {},
            onLogClick = {},
        )
    }
}

private fun previewLogs() = listOf(
    LogEntity(
        id = 1,
        tag = "top.kagg886.pmf.ui.screen.main.MainViewModel",
        severity = Severity.Info.ordinal,
        message = "Main state loaded successfully",
        timestamp = Instant.parse("2026-08-01T11:59:00Z"),
    ),
    LogEntity(
        id = 2,
        tag = "top.kagg886.pmf.data.Repository",
        severity = Severity.Error.ordinal,
        message = "Loading failed; setting state to Error",
        timestamp = Instant.parse("2026-08-01T12:00:00Z"),
        stacktrace = "IllegalStateException: response body was empty\n\tat Repository.load(Repository.kt:42)",
    ),
)
