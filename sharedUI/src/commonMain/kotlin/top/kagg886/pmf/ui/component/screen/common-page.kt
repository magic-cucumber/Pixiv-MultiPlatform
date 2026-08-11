package top.kagg886.pmf.ui.component.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.common_page_empty_summary
import top.kagg886.pmf.i18n.common_page_empty_title
import top.kagg886.pmf.i18n.common_page_refresh
import top.kagg886.pmf.i18n.logger_open
import top.kagg886.pmf.ui.component.EmptyScreen

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/8/11 22:42
 * ================================================
 */

@Composable
internal fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorContent(
    onRetry: () -> Unit,
    onViewLogClicked: () -> Unit,
    title: @Composable () -> Unit,
    summary: @Composable () -> Unit,
    retryText: @Composable () -> Unit,
) {
    EmptyScreen(
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
            )
        },
        title = title,
        summary = summary,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onViewLogClicked) {
                    Text(stringResource(Lang.string.logger_open))
                }
                Button(onClick = onRetry) {
                    retryText()
                }
            }
        },
    )
}

@Composable
internal fun EmptyContent(onRefresh: () -> Unit) {
    EmptyScreen(
        title = {

            Text(stringResource(Lang.string.common_page_empty_title))
        },
        summary = {
            Text(stringResource(Lang.string.common_page_empty_summary))
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        },
        actions = {
            Button(onClick = onRefresh) {
                Text(stringResource(Lang.string.common_page_refresh))
            }
        },
    )
}
