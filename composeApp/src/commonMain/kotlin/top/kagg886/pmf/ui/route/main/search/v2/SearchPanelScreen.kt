package top.kagg886.pmf.ui.route.main.search.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.dokar.chiptextfield.Chip
import com.dokar.chiptextfield.ChipTextFieldState
import com.dokar.chiptextfield.m3.ChipTextField
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pixko.Tag
import top.kagg886.pixko.module.illust.get
import top.kagg886.pixko.module.search.SearchSort
import top.kagg886.pixko.module.search.SearchTarget
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailRoute
import top.kagg886.pmf.ui.route.main.detail.novel.NovelDetailRoute
import top.kagg886.pmf.ui.route.main.search.v2.components.SearchPropertiesPanel
import top.kagg886.pmf.ui.route.main.series.novel.NovelSeriesRoute
import top.kagg886.pmf.ui.util.AuthorCard
import top.kagg886.pmf.ui.util.removeLastOrNullWorkaround
import top.kagg886.pmf.util.getString
import top.kagg886.pmf.util.stringResource

@Serializable
data class SearchPanelRoute(
    val sort: SearchSort = SearchSort.DATE_DESC,
    val target: SearchTarget = SearchTarget.PARTIAL_MATCH_FOR_TAGS,
    val keyword: List<String> = listOf(),
    val initialText: String = "",
) : NavKey

@Composable
fun SearchPanelScreen(route: SearchPanelRoute) {
    val (sort, target, keyword, initialText) = route
    val model = koinViewModel<SearchPanelViewModel> { parametersOf(sort, target, keyword, initialText) }
    val state by model.collectAsState()
    val stack = LocalNavBackStack.current
    val snackbarHostState = remember { SnackbarHostState() }

    model.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is SearchPanelSideEffect.Toast -> {
                snackbarHostState.showSnackbar(sideEffect.message)
            }
        }
    }

    val chipState = remember(state.keyword) {
        ChipTextFieldState(chips = state.keyword.map { Chip(it) })
    }

    LaunchedEffect(chipState.chips) {
        val target = chipState.chips.map { it.text }
        if (target.size == state.keyword.size && (target zip state.keyword).all { it.first == it.second }) {
            return@LaunchedEffect
        }
        model.updateKeywords(target)
    }

    Scaffold(
        topBar = {
            when (state.target) {
                SearchTarget.EXACT_MATCH_FOR_TAGS, SearchTarget.PARTIAL_MATCH_FOR_TAGS -> {
                    LaunchedEffect(Unit) {
                        snapshotFlow { state.text }.debounce(0.5.seconds)
                            .distinctUntilChanged()
                            .collectLatest { msg ->
                                if (msg.isNotBlank()) {
                                    model.searchTagOrExactSearch(msg)
                                }
                            }
                    }
                    ChipTextField(
                        state = chipState,
                        value = state.text,
                        onValueChange = { model.updateText(it) },
                        onSubmit = {
                            model.selectTag(Tag(it))
                            model.updateText("")
                            Chip(it)
                        },
                        leadingIcon = {
                            IconButton(onClick = { stack.removeLastOrNullWorkaround() }) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, null)
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (state.keyword.isNotEmpty()) {
                                        stack += SearchResultRoute(
                                            state.keyword,
                                            state.sort,
                                            state.target,
                                        )
                                    }
                                },
                                enabled = state.keyword.isNotEmpty() && state.panelState !is SearchPanelState.RedirectToPage,
                            ) {
                                Icon(Icons.Default.Search, null)
                            }
                        },
                    )
                }

                else -> {
                    LaunchedEffect(Unit) {
                        model.updateKeywords(listOf())
                        model.updateText(initialText)
                    }
                    TextField(
                        value = state.text,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { model.updateText(it) },
                        leadingIcon = {
                            IconButton(onClick = { stack.removeLastOrNullWorkaround() }) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, null)
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (state.text.isNotEmpty()) {
                                        stack += SearchResultRoute(
                                            listOf(state.text),
                                            state.sort,
                                            state.target,
                                        )
                                    }
                                },
                                enabled = state.text.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Search, null)
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            AnimatedContent(state.panelState) { currentState ->
                when (currentState) {
                    is SearchPanelState.SettingProperties -> {
                        SearchPropertiesPanel(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            sort = state.sort,
                            target = state.target,
                            tag = state.hotTag,
                            onSortChange = { model.updateSort(it) },
                            onTargetChange = { model.updateTarget(it) },
                            onTagRequestRefresh = { model.refreshHotTag() },
                            onTagClicked = { t -> model.selectTag(t.tag) },
                        )
                    }

                    is SearchPanelState.Searching -> {
                        Loading()
                    }

                    is SearchPanelState.SearchingFailed -> {
                        ErrorPage(
                            text = currentState.msg,
                            onClick = {
                                model.searchTagOrExactSearch(state.text)
                            },
                        )
                    }

                    is SearchPanelState.SelectTag -> {
                        LazyColumn {
                            items(currentState.tags) {
                                ListItem(
                                    headlineContent = {
                                        Text(it.name)
                                    },
                                    supportingContent = {
                                        Text(it.translatedName ?: "")
                                    },
                                    modifier = Modifier.clickable {
                                        model.selectTag(it)
                                    },
                                )
                            }
                        }
                    }

                    is SearchPanelState.RedirectToPage -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                if (currentState.illust != null) {
                                    ListItem(
                                        overlineContent = {
                                            Text(stringResource(Res.string.found_illust))
                                        },
                                        leadingContent = {
                                            AsyncImage(
                                                model = currentState.illust.contentImages.get()!![0],
                                                modifier = Modifier.height(144.dp).aspectRatio(
                                                    ratio = currentState.illust.width / currentState.illust.height.toFloat(),
                                                ),
                                                contentDescription = null,
                                            )
                                        },
                                        headlineContent = {
                                            Text(currentState.illust.title)
                                        },
                                        supportingContent = {
                                            AuthorCard(
                                                user = currentState.illust.user,
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            stack += IllustDetailRoute(currentState.illust)
                                        },
                                    )
                                }
                            }

                            item {
                                if (currentState.novel != null) {
                                    ListItem(
                                        overlineContent = {
                                            Text(stringResource(Res.string.found_novel))
                                        },
                                        leadingContent = {
                                            AsyncImage(
                                                model = currentState.novel.imageUrls.medium!!,
                                                modifier = Modifier.height(144.dp).aspectRatio(70 / 144f),
                                                contentDescription = null,
                                            )
                                        },
                                        headlineContent = {
                                            Text(currentState.novel.title)
                                        },
                                        supportingContent = {
                                            Text(currentState.novel.caption, maxLines = 3)
                                        },
                                        modifier = Modifier.clickable {
                                            stack += NovelDetailRoute(currentState.novel.id.toLong())
                                        },
                                    )
                                }
                            }

                            item {
                                if (currentState.series != null) {
                                    ListItem(
                                        overlineContent = {
                                            Text(stringResource(Res.string.found_novel_series))
                                        },
                                        headlineContent = {
                                            Text(currentState.series.novelSeriesDetail.title)
                                        },
                                        modifier = Modifier.clickable {
                                            stack += NovelSeriesRoute(currentState.series.novelSeriesDetail.id)
                                        },
                                    )
                                }
                            }

                            item {
                                if (currentState.user != null) {
                                    val toast = LocalSnackBarHost.current
                                    ListItem(
                                        overlineContent = {
                                            Text(stringResource(Res.string.found_user))
                                        },
                                        headlineContent = {},
                                        supportingContent = {
                                            AuthorCard(
                                                user = currentState.user.user,
                                                onFavoriteClick = {
                                                    toast.showSnackbar(getString(Res.string.please_bookmark_author_to_detail))
                                                },
                                            )
                                        },
                                    )
                                }
                            }

                            item {
                                if (currentState.illust == null &&
                                    currentState.novel == null &&
                                    currentState.user == null &&
                                    currentState.series == null
                                ) {
                                    ErrorPage(
                                        text = stringResource(Res.string.search_result_none),
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
