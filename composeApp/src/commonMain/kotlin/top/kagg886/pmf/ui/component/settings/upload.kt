@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.alorma.compose.settings.ui.SettingsTileDefaults
import com.alorma.compose.settings.ui.SettingsTileScaffold
import kotlinx.coroutines.launch
import okio.BufferedSource
import okio.buffer
import okio.use
import top.kagg886.filepicker.FilePicker
import top.kagg886.filepicker.openFilePicker

@Composable
fun SettingsFileUpload(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: @Composable () -> Unit,
    extensions: List<String> = listOf(),
    subTitle: @Composable () -> Unit = {},
    onValueChange: (ByteArray) -> Unit,
) {
    val scope = rememberCoroutineScope()
//    val launcher = rememberFilePickerLauncher(type = FileKitType.File(extensions)) {
//        if (it != null) {
//            scope.launch {
//                onValueChange(it.readBytes())
//            }
//        }
//    }

    val colors = SettingsTileDefaults.colors()

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
            subTitle()
        }
    }

    SettingsTileScaffold(
        title = decoratedTitle,
        subtitle = decoratedSubTitle,
        modifier = modifier.clickable(enabled) {
            scope.launch {
                val path = FilePicker.openFilePicker(
                    ext = extensions,
                )
                if (path != null) {
                    onValueChange(path.buffer().use(BufferedSource::readByteArray))
                }
            }
        },
    )
}

@Composable
private fun ProvideContentColorAndTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides textStyle,
    ) {
        content()
    }
}

// @Composable
// fun SettingsTileScaffold(
//  title: @Composable () -> Unit,
//  modifier: Modifier = Modifier,
//  enabled: Boolean = true,
//  subtitle: @Composable (() -> Unit)? = null,
//  icon: @Composable (() -> Unit)? = null,
//  colors: SettingsTileColors = SettingsTileDefaults.colors(),
//  textStyles: SettingsTextStyles = SettingsTileDefaults.textStyles(),
//  shape: Shape = SettingsTileDefaults.shape(),
//  tonalElevation: Dp = SettingsTileDefaults.Elevation,
//  shadowElevation: Dp = SettingsTileDefaults.Elevation,
//  action: @Composable (() -> Unit)? = null,
// ) {

//
//  ListItem(
//    modifier = Modifier.fillMaxWidth().clip(shape).then(modifier),
//    headlineContent = decoratedTitle,
//    supportingContent = decoratedSubtitle,
//    leadingContent = decoratedIcon,
//    trailingContent = decoratedAction,
//    colors =
//      ListItemColors(
//        containerColor = colors.containerColor,
//        headlineColor = colors.titleColor,
//        leadingIconColor = colors.iconColor,
//        overlineColor = colors.actionColor,
//        supportingTextColor = colors.subtitleColor,
//        trailingIconColor = colors.actionColor,
//        disabledHeadlineColor = colors.disabledTitleColor,
//        disabledLeadingIconColor = colors.disabledIconColor,
//        disabledTrailingIconColor = colors.disabledActionColor,
//      ),
//    tonalElevation = tonalElevation,
//    shadowElevation = shadowElevation,
//  )
// }
