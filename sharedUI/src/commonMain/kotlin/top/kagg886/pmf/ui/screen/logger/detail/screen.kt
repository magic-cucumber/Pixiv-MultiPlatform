package top.kagg886.pmf.ui.screen.logger.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.database.common.entity.LogEntity
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_detail_close
import top.kagg886.pmf.i18n.logger_detail_copy
import top.kagg886.pmf.i18n.logger_detail_id
import top.kagg886.pmf.i18n.logger_detail_menu
import top.kagg886.pmf.i18n.logger_detail_message
import top.kagg886.pmf.i18n.logger_detail_no_stacktrace
import top.kagg886.pmf.i18n.logger_detail_severity
import top.kagg886.pmf.i18n.logger_detail_stacktrace
import top.kagg886.pmf.i18n.logger_detail_tag
import top.kagg886.pmf.i18n.logger_detail_time
import top.kagg886.pmf.i18n.logger_detail_title
import top.kagg886.pmf.ui.component.LocalSnackBarHostState
import top.kagg886.pmf.ui.component.bottomsheet.BottomSheetPageScaffold
import top.kagg886.pmf.ui.component.showSnackBar
import top.kagg886.pmf.ui.screen.logger.list.formatTimestamp
import top.kagg886.pmf.ui.screen.logger.list.severityLabel
import top.kagg886.pmf.ui.util.createMenuButtonAnim
import top.kagg886.pmf.util.nav3.SerializableNavKey
import kotlin.time.Instant

@Serializable
data class LoggerDetailRoute(
    val id: Long,
    val tag: String,
    val severity: Int,
    val message: String,
    val timestamp: Long,
    val stacktrace: String?,
) : SerializableNavKey {
    constructor(log: LogEntity) : this(
        id = log.id,
        tag = log.tag,
        severity = log.severity,
        message = log.message,
        timestamp = log.timestamp.toEpochMilliseconds(),
        stacktrace = log.stacktrace,
    )
}

@Composable
fun LoggerDetailScreen(route: LoggerDetailRoute) {
    val log = LogEntity(
        id = route.id,
        tag = route.tag,
        severity = route.severity,
        message = route.message,
        timestamp = Instant.fromEpochMilliseconds(route.timestamp),
        stacktrace = route.stacktrace,
    )

    BottomSheetPageScaffold {
        Column(modifier = Modifier.matchContent()) {
            val clipboard = LocalClipboardManager.current
            val logText = log.toCopyText()
            var menuExpanded by remember { mutableStateOf(false) }

            TopAppBar(
                windowInsets = WindowInsets(),
                navigationIcon = {
                    IconButton(onClick = { close() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Lang.string.logger_detail_close),
                        )
                    }
                },
                title = {
                    Text(text = stringResource(Lang.string.logger_detail_title))
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = !menuExpanded }) {
                            AnimatedContent(
                                targetState = menuExpanded,
                                transitionSpec = createMenuButtonAnim { targetState },
                                label = "logger detail menu icon",
                            ) { expanded ->
                                Icon(
                                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.MoreVert,
                                    contentDescription = stringResource(Lang.string.logger_detail_menu),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Lang.string.logger_detail_copy)) },
                                onClick = {
                                    clipboard.setText(AnnotatedString(logText))
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailRow(
                    label = stringResource(Lang.string.logger_detail_id),
                    value = log.id.toString(),
                )
                DetailRow(
                    label = stringResource(Lang.string.logger_detail_tag),
                    value = log.tag,
                )
                DetailRow(
                    label = stringResource(Lang.string.logger_detail_severity),
                    value = severityLabel(log.severity),
                )
                DetailRow(
                    label = stringResource(Lang.string.logger_detail_time),
                    value = formatTimestamp(log.timestamp),
                )
                DetailRow(
                    label = stringResource(Lang.string.logger_detail_message),
                    value = log.message,
                )
                Text(
                    text = stringResource(Lang.string.logger_detail_stacktrace),
                    style = MaterialTheme.typography.titleMedium,
                )
                SelectionContainer {
                    Text(
                        text = log.stacktrace ?: stringResource(Lang.string.logger_detail_no_stacktrace),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntity.toCopyText(): String {
    val idLabel = stringResource(Lang.string.logger_detail_id)
    val tagLabel = stringResource(Lang.string.logger_detail_tag)
    val severityLabelText = stringResource(Lang.string.logger_detail_severity)
    val timeLabel = stringResource(Lang.string.logger_detail_time)
    val messageLabel = stringResource(Lang.string.logger_detail_message)
    val stacktraceLabel = stringResource(Lang.string.logger_detail_stacktrace)
    val noStacktrace = stringResource(Lang.string.logger_detail_no_stacktrace)
    val severityText = severityLabel(severity)
    val timeText = formatTimestamp(timestamp)
    return remember(
        idLabel, tagLabel, severityLabelText, timeLabel, messageLabel,
        stacktraceLabel, noStacktrace, severityText, timeText, this,
    ) {
        buildString {
            appendLine("$idLabel: $id")
            appendLine("$tagLabel: $tag")
            appendLine("$severityLabelText: $severityText")
            appendLine("$timeLabel: $timeText")
            appendLine("$messageLabel: $message")
            append(stacktraceLabel).appendLine(":")
            append(stacktrace ?: noStacktrace)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
