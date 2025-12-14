package top.kagg886.pmf.ui.route.main.setting.filter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.alorma.compose.settings.ui.SettingsSlider
import com.alorma.compose.settings.ui.SettingsSwitch
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.BlackListType
import top.kagg886.pmf.backend.database.dao.name
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.TabContainer
import top.kagg886.pmf.ui.component.dialog.DialogPageScaffold
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.component.settings.SettingsDropdownMenu
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

    TAG,

    USER,
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
                    TAG -> Text(stringResource(Res.string.tags))
                    USER -> Text(stringResource(Res.string.user))
                }
            },
            current = data,
            onCurrentChange = { data = it },
            page = {
                when (it) {
                    ILLUST, NOVEL -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (it) {
                                ILLUST -> SettingFilterScreenIllust()
                                NOVEL -> SettingFilterScreenNovel()
                                else -> {}
                            }
                        }
                    }
                    USER -> SettingsFilterScreenUser()
                    TAG -> SettingFilterScreenTags()
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

    var filterAspectRatioType by remember {
        mutableStateOf(AppConfig.filterAspectRatioType)
    }

    LaunchedEffect(filterAspectRatioType) {
        AppConfig.filterAspectRatioType = filterAspectRatioType
    }

    SettingsDropdownMenu(
        title = {
            Text(stringResource(Res.string.options_filter_aspect_ratio))
        },
        subTitle = {
            Text(stringResource(Res.string.option_filter_aspect_ratio_usage))
        },
        optionsFormat = {
            when (it) {
                AppConfig.AspectRatioFilterType.NONE -> stringResource(Res.string.option_filter_aspect_ratio_none)
                AppConfig.AspectRatioFilterType.PHONE -> stringResource(Res.string.option_filter_aspect_ratio_phone)
                AppConfig.AspectRatioFilterType.PC -> stringResource(Res.string.option_filter_aspect_ratio_pc)
            }
        },
        current = filterAspectRatioType,
        data = AppConfig.AspectRatioFilterType.entries,
        onSelected = {
            filterAspectRatioType = it
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

    var filterLongTag by remember {
        mutableStateOf(AppConfig.filterLongTag)
    }
    var filterLongTagLength by remember {
        mutableStateOf(AppConfig.filterLongTagMinLength)
    }
    LaunchedEffect(filterLongTag) {
        AppConfig.filterLongTag = filterLongTag
        if (!filterLongTag) {
            filterLongTagLength = 15
        }
    }
    SettingsSwitch(
        state = filterLongTag,
        title = {
            Text(stringResource(Res.string.filter_long_tag))
        },
        subtitle = {
            Text(stringResource(Res.string.filter_long_tag_description))
        },
        onCheckedChange = {
            filterLongTag = it
        },
    )
    SettingsSlider(
        enabled = filterLongTag,
        title = {
            Text(stringResource(Res.string.tag_max_length))
        },
        subtitle = {
            Column {
                Text(stringResource(Res.string.tag_max_length_description))
                key(filterLongTagLength) { Text(stringResource(Res.string.current_value, filterLongTagLength)) }
            }
        },
        value = filterLongTagLength.toFloat(),
        valueRange = 5f..25f,
        onValueChange = {
            filterLongTagLength = it.roundToInt()
        },
    )

    var filterShortNovel by remember {
        mutableStateOf(AppConfig.filterShortNovel)
    }
    var filterShortNovelLength by remember {
        mutableStateOf(AppConfig.filterShortNovelMaxLength)
    }
    LaunchedEffect(filterShortNovel) {
        AppConfig.filterShortNovel = filterShortNovel
        if (!filterShortNovel) {
            filterShortNovelLength = 100
        }
    }
    SettingsSwitch(
        state = filterShortNovel,
        title = {
            Text(stringResource(Res.string.filter_short_novel))
        },
        subtitle = {
            Text(stringResource(Res.string.filter_short_novel_description))
        },
        onCheckedChange = {
            filterShortNovel = it
        },
    )
    SettingsSlider(
        enabled = filterShortNovel,
        title = {
            Text(stringResource(Res.string.novel_filter_length))
        },
        subtitle = {
            Column {
                Text(stringResource(Res.string.novel_filter_length_description))
                key(filterShortNovelLength) {
                    Text(
                        stringResource(
                            Res.string.current_value,
                            filterShortNovelLength,
                        ),
                    )
                }
            }
        },
        value = filterShortNovelLength.toFloat(),
        valueRange = 30f..1000f,
        steps = 968,
        onValueChange = {
            filterShortNovelLength = it.roundToInt()
        },
    )
}

@Composable
private fun SettingFilterScreenTags(database: AppDatabase = koinInject()) {
    val pager = remember {
        Pager(PagingConfig(pageSize = 30)) {
            database.blacklistDAO().query(BlackListType.TAG_NAME)
        }
    }

    val data = pager.flow.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()

    when {
        !data.loadState.isIdle && data.itemCount == 0 -> Loading()
        else -> {
            if (data.itemCount == 0 && data.loadState.isIdle) {
                ErrorPage(text = stringResource(Res.string.page_is_empty)) {
                    data.retry()
                }
                return
            }

            val lazyItemState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyItemState,
                    modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                ) {
                    items(
                        count = data.itemCount,
                        key = { i -> data.peek(i)!!.id!! },
                    ) { i ->
                        val item = data[i]!!
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            headlineContent = {
                                Text(item.name)
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            database.blacklistDAO().delete(item.id!!)
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            },
                        )
                    }
                    item(key = "Footer") {
                        if (!data.loadState.isIdle) {
                            Loading()
                        } else {
                            Text(
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(Res.string.no_more_data),
                            )
                        }
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(lazyItemState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsFilterScreenUser(database: AppDatabase = koinInject()) {
    val pager = remember {
        Pager(PagingConfig(pageSize = 30)) {
            database.blacklistDAO().query(BlackListType.AUTHOR_ID)
        }
    }

    val data = pager.flow.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()

    when {
        !data.loadState.isIdle && data.itemCount == 0 -> Loading()
        else -> {
            if (data.itemCount == 0 && data.loadState.isIdle) {
                ErrorPage(text = stringResource(Res.string.page_is_empty)) {
                    data.retry()
                }
                return
            }

            val lazyItemState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyItemState,
                    modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                ) {
                    items(
                        count = data.itemCount,
                        key = { i -> data.peek(i)!!.id!! },
                    ) { i ->
                        val item = data[i]!!
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                if (item.meta != null) {
                                    AsyncImage(
                                        model = item.meta.profileImageUrls.content,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentDescription = null,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("?")
                                    }
                                }
                            },
                            headlineContent = {
                                Text(item.meta?.name ?: item.payload)
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            database.blacklistDAO().delete(item.id!!)
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            },
                        )
                    }
                    item(key = "Footer") {
                        if (!data.loadState.isIdle) {
                            Loading()
                        } else {
                            Text(
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(Res.string.no_more_data),
                            )
                        }
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(lazyItemState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
                )
            }
        }
    }
}
