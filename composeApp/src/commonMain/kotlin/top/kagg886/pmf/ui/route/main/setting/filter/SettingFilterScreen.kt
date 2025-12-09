package top.kagg886.pmf.ui.route.main.setting.filter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlinx.serialization.Serializable
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.res.Res
import top.kagg886.pmf.res.confirm
import top.kagg886.pmf.res.filter_ai_illustrations
import top.kagg886.pmf.res.filter_ai_novel
import top.kagg886.pmf.res.filter_ai_server_hint
import top.kagg886.pmf.res.filter_r18_benefit
import top.kagg886.pmf.res.filter_r18_description
import top.kagg886.pmf.res.filter_r18_illustrations
import top.kagg886.pmf.res.filter_r18_novel
import top.kagg886.pmf.res.filter_r18g_description
import top.kagg886.pmf.res.filter_r18g_enabled_condition
import top.kagg886.pmf.res.filter_r18g_illustrations
import top.kagg886.pmf.res.filter_r18g_novel
import top.kagg886.pmf.res.illust
import top.kagg886.pmf.res.novel
import top.kagg886.pmf.res.settings_filter
import top.kagg886.pmf.ui.component.TabContainer
import top.kagg886.pmf.ui.component.dialog.DialogPageScaffold
import top.kagg886.pmf.ui.route.main.setting.filter.SettingFilterNavigationKey.*
import top.kagg886.pmf.ui.util.removeLastOrNullWorkaround
import top.kagg886.pmf.util.stringResource

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/12/9 10:12
 * ================================================
 */

@Serializable
data object SettingFilterRoute : NavKey

@Serializable
private enum class SettingFilterNavigationKey {
    ILLUST,
    NOVEL,
}

@Composable
fun SettingFilterScreen() = DialogPageScaffold(
    title = { Text(stringResource(Res.string.settings_filter)) },
    confirmButton = {
        val nav = LocalNavBackStack.current
        TextButton(
            onClick = {
                nav.removeLastOrNullWorkaround()
            },
        ) {
            Text(stringResource(Res.string.confirm))
        }
    },
    text = {
        var data by remember {
            mutableStateOf(
                SettingFilterNavigationKey.ILLUST,
            )
        }
        TabContainer(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            tab = SettingFilterNavigationKey.entries.toList(),
            tabTitle = {
                when (it) {
                    ILLUST -> Text(stringResource(Res.string.illust))
                    NOVEL -> Text(stringResource(Res.string.novel))
                }
            },
            current = data,
            onCurrentChange = { data = it },
            page = {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (it) {
                        ILLUST -> SettingFilterScreenIllust()
                        NOVEL -> SettingFilterScreenNovel()
                    }
                }
            },
        )
    },
)

@Composable
private fun SettingFilterScreenIllust() {
    var filterAi by remember {
        mutableStateOf(AppConfig.filterAi)
    }
    LaunchedEffect(filterAi) {
        AppConfig.filterAi = filterAi
    }
    SettingsSwitch(
        state = filterAi,
        title = {
            Text(stringResource(Res.string.filter_ai_illustrations))
        },
        subtitle = {
            Text(stringResource(Res.string.filter_ai_server_hint))
        },
        onCheckedChange = {
            filterAi = it
        },
    )

    var filterR18 by remember {
        mutableStateOf(AppConfig.filterR18)
    }
    var filterR18G by remember {
        mutableStateOf(AppConfig.filterR18G)
    }
    LaunchedEffect(filterR18) {
        AppConfig.filterR18 = filterR18
    }
    SettingsSwitch(
        state = filterR18,
        title = {
            Text(stringResource(Res.string.filter_r18_illustrations))
        },
        subtitle = {
            Column {
                Text(stringResource(Res.string.filter_r18_description))
                Text(stringResource(Res.string.filter_r18_benefit))
            }
        },
        onCheckedChange = {
            filterR18 = it
        },
    )
    LaunchedEffect(filterR18G) {
        AppConfig.filterR18G = filterR18G
    }
    SettingsSwitch(
        state = filterR18G,
        enabled = !filterR18, // 不过滤r18时启用
        title = {
            Text(stringResource(Res.string.filter_r18g_illustrations))
        },
        subtitle = {
            Column {
                Text(stringResource(Res.string.filter_r18g_description))
                Text(stringResource(Res.string.filter_r18g_enabled_condition))
            }
        },
        onCheckedChange = {
            filterR18G = it
        },
    )
}

@Composable
private fun SettingFilterScreenNovel() {
    var filterAiNovel by remember {
        mutableStateOf(AppConfig.filterAiNovel)
    }
    LaunchedEffect(filterAiNovel) {
        AppConfig.filterAiNovel = filterAiNovel
    }
    SettingsSwitch(
        state = filterAiNovel,
        title = {
            Text(stringResource(Res.string.filter_ai_novel))
        },
        subtitle = {
            Text(stringResource(Res.string.filter_ai_server_hint))
        },
        onCheckedChange = {
            filterAiNovel = it
        },
    )

    var filterR18Novel by remember {
        mutableStateOf(AppConfig.filterR18Novel)
    }
    var filterR18GNovel by remember {
        mutableStateOf(AppConfig.filterR18GNovel)
    }
    LaunchedEffect(filterR18Novel) {
        AppConfig.filterR18Novel = filterR18Novel
    }
    SettingsSwitch(
        state = filterR18Novel,
        title = {
            Text(stringResource(Res.string.filter_r18_novel))
        },
        subtitle = {
            Column {
                Text(stringResource(Res.string.filter_r18_description))
                Text(stringResource(Res.string.filter_r18_benefit))
            }
        },
        onCheckedChange = {
            filterR18Novel = it
        },
    )
    LaunchedEffect(filterR18GNovel) {
        AppConfig.filterR18GNovel = filterR18GNovel
    }
    SettingsSwitch(
        state = filterR18GNovel,
        enabled = !filterR18Novel, // 不过滤r18时启用
        title = {
            Text(stringResource(Res.string.filter_r18g_novel))
        },
        subtitle = {
            Column {
                Text(stringResource(Res.string.filter_r18g_description))
                Text(stringResource(Res.string.filter_r18g_enabled_condition))
            }
        },
        onCheckedChange = {
            filterR18GNovel = it
        },
    )
}
