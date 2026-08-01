package top.kagg886.pmf.ui.screen.logger.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.database.common.entity.LogEntity
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.logger_detail_close
import top.kagg886.pmf.i18n.logger_detail_id
import top.kagg886.pmf.i18n.logger_detail_message
import top.kagg886.pmf.i18n.logger_detail_no_stacktrace
import top.kagg886.pmf.i18n.logger_detail_severity
import top.kagg886.pmf.i18n.logger_detail_stacktrace
import top.kagg886.pmf.i18n.logger_detail_tag
import top.kagg886.pmf.i18n.logger_detail_time
import top.kagg886.pmf.i18n.logger_detail_title
import top.kagg886.pmf.ui.screen.logger.list.formatTimestamp
import top.kagg886.pmf.ui.screen.logger.list.severityLabel
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
    val nav = LocalNavController.current
    val log = LogEntity(
        id = route.id,
        tag = route.tag,
        severity = route.severity,
        message = route.message,
        timestamp = Instant.fromEpochMilliseconds(route.timestamp),
        stacktrace = route.stacktrace,
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Lang.string.logger_detail_title),
                style = MaterialTheme.typography.headlineSmall,
            )
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
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { nav.popBackStack() },
            ) {
                Text(stringResource(Lang.string.logger_detail_close))
            }
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
