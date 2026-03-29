@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.alorma.compose.settings.ui.SettingsTileDefaults
import com.alorma.compose.settings.ui.SettingsTileScaffold
import com.alorma.compose.settings.ui.core.LocalSettingsGroupEnabled

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/3/29 16:12
 * ================================================
 */

@Composable
fun SettingsMenuLink(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalSettingsGroupEnabled.current,
    icon: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    colors: ListItemColors = SettingsTileDefaults.colors(),
    shape: Shape = SettingsTileDefaults.shape(),
    tonalElevation: Dp = SettingsTileDefaults.Elevation,
    shadowElevation: Dp = SettingsTileDefaults.Elevation,
    semanticProperties: (SemanticsPropertyReceiver.() -> Unit) = {},
    onClick: () -> Unit,
) {
    val decoratedTitle: @Composable () -> Unit = {
        ProvideContentColorAndTextStyle(
            contentColor = colors.headlineColor(enabled),
            textStyle = MaterialTheme.typography.bodyLarge,
        ) {
            title()
        }
    }

    val decoratedSubTitle: @Composable (() -> Unit) = {
        ProvideContentColorAndTextStyle(
            contentColor = colors.headlineColor(enabled),
            textStyle = MaterialTheme.typography.bodyMedium,
        ) {
            subtitle?.let { it() }
        }
    }

    SettingsTileScaffold(
        modifier = Modifier
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ).semantics(properties = semanticProperties)
            .then(modifier),
        title = decoratedTitle,
        subtitle = decoratedSubTitle,
        icon = icon,
        colors = colors,
        shape = shape,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        action = action,
    )
}
