@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsTileDefaults
import com.alorma.compose.settings.ui.core.LocalSettingsGroupEnabled

@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    colors: ListItemColors = SettingsTileDefaults.colors(),
    title: @Composable (() -> Unit)? = null,
    semanticProperties: (SemanticsPropertyReceiver.() -> Unit) = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics(properties = semanticProperties)
            .then(modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary),
                ) {
                    SettingsGroupTitle(title)
                }
            }
        }
        CompositionLocalProvider(LocalSettingsGroupEnabled provides enabled) {
            content()
        }
    }
}

@Composable
internal fun SettingsGroupTitle(title: @Composable () -> Unit) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        title()
    }
}

// object SettingsTileDefaults {
//  val Elevation: Dp = ListItemDefaults.Elevation
//  const val DisabledAlpha: Float = 0.38f
//
//  @Composable
//  fun shape(): Shape = ListItemDefaults.shape
//
//  @Composable
//  fun textStyles(
//    groupTitleStyle: TextStyle =
//      LocalSettingsTextStyles.current?.groupTitleStyle
//        ?: MaterialTheme.typography.titleMedium,
//    titleStyle: TextStyle =
//      LocalSettingsTextStyles.current?.titleStyle
//        ?: MaterialTheme.typography.bodyLarge,
//    subtitleStyle: TextStyle =
//      LocalSettingsTextStyles.current?.subtitleStyle
//        ?: MaterialTheme.typography.bodyMedium,
//  ): SettingsTextStyles =
//    SettingsTextStyles(
//      groupTitleStyle = groupTitleStyle,
//      titleStyle = titleStyle,
//      subtitleStyle = subtitleStyle,
//    )
//
//  @Composable
//  fun colors(
//    containerColor: Color =
//      LocalSettingsTileColors.current?.containerColor
//        ?: MaterialTheme.colorScheme.surface,
//    titleColor: Color =
//      LocalSettingsTileColors.current?.titleColor
//        ?: MaterialTheme.colorScheme.primary,
//    groupTitleColor: Color =
//      LocalSettingsTileColors.current?.groupTitleColor
//        ?: MaterialTheme.colorScheme.onBackground,
//    iconColor: Color = LocalSettingsTileColors.current?.iconColor ?: MaterialTheme.colorScheme.onSurface,
//    subtitleColor: Color = LocalSettingsTileColors.current?.subtitleColor ?: MaterialTheme.colorScheme.onSurface,
//    actionColor: Color =
//      LocalSettingsTileColors.current?.actionColor
//        ?: MaterialTheme.colorScheme.primary,
//    disabledTitleColor: Color = titleColor.copy(alpha = DisabledAlpha),
//    disabledGroupTitleColor: Color = groupTitleColor.copy(alpha = DisabledAlpha),
//    disabledIconColor: Color = iconColor.copy(alpha = DisabledAlpha),
//    disabledSubtitleColor: Color = subtitleColor.copy(alpha = DisabledAlpha),
//    disabledActionColor: Color = actionColor.copy(alpha = DisabledAlpha),
//  ): SettingsTileColors =
//    SettingsTileColors(
//      containerColor = containerColor,
//      titleColor = titleColor,
//      groupTitleColor = groupTitleColor,
//      iconColor = iconColor,
//      subtitleColor = subtitleColor,
//      actionColor = actionColor,
//      disabledTitleColor = disabledTitleColor,
//      disabledGroupTitleColor = disabledGroupTitleColor,
//      disabledIconColor = disabledIconColor,
//      disabledSubtitleColor = disabledSubtitleColor,
//      disabledActionColor = disabledActionColor,
//    )
// }

// @Immutable
// class SettingsTileColors(
//  val containerColor: Color,
//  val titleColor: Color,
//  val groupTitleColor: Color,
//  val iconColor: Color,
//  val subtitleColor: Color,
//  val actionColor: Color,
//  val disabledTitleColor: Color,
//  val disabledGroupTitleColor: Color,
//  val disabledIconColor: Color,
//  val disabledSubtitleColor: Color,
//  val disabledActionColor: Color,
// ) {
//  @Stable
//  fun groupTitleColor(enabled: Boolean): Color = if (enabled) groupTitleColor else disabledGroupTitleColor
//
//  @Stable
//  fun titleColor(enabled: Boolean): Color = if (enabled) titleColor else disabledTitleColor
//
//  @Stable
//  fun iconColor(enabled: Boolean): Color = if (enabled) iconColor else disabledIconColor
//
//  @Stable
//  fun subtitleColor(enabled: Boolean): Color = if (enabled) subtitleColor else disabledSubtitleColor
//
//  @Stable
//  fun actionColor(enabled: Boolean): Color = if (enabled) actionColor else disabledActionColor
// }
