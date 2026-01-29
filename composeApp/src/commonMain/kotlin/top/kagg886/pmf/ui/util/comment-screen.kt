package top.kagg886.pmf.ui.util

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalNavBackStack
import top.kagg886.pmf.LocalSnackBarHost
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.BackToTopOrRefreshButton
import top.kagg886.pmf.ui.component.ErrorPage
import top.kagg886.pmf.ui.component.FavoriteButton
import top.kagg886.pmf.ui.component.Loading
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.route.main.detail.author.AuthorRoute
import top.kagg886.pmf.util.stringResource
import top.kagg886.pmf.util.toReadableString

@Composable
fun CommentPanel(model: CommentViewModel, modifier: Modifier = Modifier) {
    val state by model.collectAsState()
    CommentPanelContainer(model, state, modifier)
}

@Composable
private fun CommentPanelContainer(model: CommentViewModel, state: CommentViewState, modifier: Modifier) {
    val stack = LocalNavBackStack.current
    val data = model.data.collectAsLazyPagingItems()

    val medias by produceState(initialValue = CommentMedia(emptyMap(), emptyList())) {
        value = withContext(Dispatchers.Default) {
            Json.decodeFromString(
                """
                    {
                      "emojis": {
                        "normal": 101,
                        "surprise": 102,
                        "serious": 103,
                        "heaven": 104,
                        "happy": 105,
                        "excited": 106,
                        "sing": 107,
                        "cry": 108,
                        "normal2": 201,
                        "shame2": 202,
                        "love2": 203,
                        "interesting2": 204,
                        "blush2": 205,
                        "fire2": 206,
                        "angry2": 207,
                        "shine2": 208,
                        "panic2": 209,
                        "normal3": 301,
                        "satisfaction3": 302,
                        "surprise3": 303,
                        "smile3": 304,
                        "shock3": 305,
                        "gaze3": 306,
                        "wink3": 307,
                        "happy3": 308,
                        "excited3": 309,
                        "love3": 310,
                        "normal4": 401,
                        "surprise4": 402,
                        "serious4": 403,
                        "love4": 404,
                        "shine4": 405,
                        "sweat4": 406,
                        "shame4": 407,
                        "sleep4": 408,
                        "heart": 501,
                        "teardrop": 502,
                        "star": 503
                      },
                      "stamps": [
                        301,
                        302,
                        303,
                        304,
                        305,
                        306,
                        307,
                        308,
                        309,
                        310,
                        401,
                        402,
                        403,
                        404,
                        405,
                        406,
                        407,
                        408,
                        409,
                        410,
                        201,
                        202,
                        203,
                        204,
                        205,
                        206,
                        207,
                        208,
                        209,
                        210,
                        101,
                        102,
                        103,
                        104,
                        105,
                        106,
                        107,
                        108,
                        109,
                        110
                      ]
                    }
                """.trimIndent(),
            )
        }
    }

    when {
        !data.loadState.isIdle && data.itemCount == 0 -> Loading()

        state is CommentViewState.Success -> {
            val scroll = state.scrollerState
            val host = LocalSnackBarHost.current

            model.collectSideEffect {
                when (it) {
                    is CommentSideEffect.Toast -> {
                        host.showSnackbar(it.msg)
                    }
                }
            }
            val scope = rememberCoroutineScope()
            var isRefresh by remember { mutableStateOf(false) }
            Column(modifier) {
                PullToRefreshBox(
                    isRefreshing = isRefresh,
                    onRefresh = {
                        scope.launch {
                            isRefresh = true
                            model.refresh()
                            data.awaitNextState()
                            isRefresh = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    if (data.itemCount == 0 && data.loadState.isIdle) {
                        ErrorPage(text = stringResource(Res.string.page_is_empty)) {
                            data.retry()
                        }
                        return@PullToRefreshBox
                    }

                    val controller = remember {
                        keyboardScrollerController(scroll) {
                            scroll.layoutInfo.viewportSize.height.toFloat()
                        }
                    }

                    KeyListenerFromGlobalPipe(controller)

                    LazyColumn(state = scroll, modifier = Modifier.padding(end = 8.dp)) {
                        items(
                            count = data.itemCount,
                            key = { i -> data.peek(i)!!.id },
                        ) { i ->
                            val comment = data[i]!!
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth().padding(5.dp),
                            ) {
                                ListItem(
                                    overlineContent = {
                                        Text(comment.date.toReadableString())
                                    },
                                    headlineContent = {
                                        Text(comment.user.name, style = MaterialTheme.typography.labelSmall)
                                    },
                                    leadingContent = {
                                        AsyncImage(
                                            model = comment.user.profileImageUrls.content,
                                            modifier = Modifier.size(35.dp)
                                                .clickable { stack += AuthorRoute(comment.user.id) },
                                            contentDescription = null,
                                        )
                                    },
                                    trailingContent = {
                                        AnimatedContent(
                                            targetState = when {
                                                comment.hasReplies -> -1
                                                comment == (state as? CommentViewState.Success.HasReply)?.target -> 1
                                                else -> 0
                                            },
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        ) {
                                            when (it) {
                                                -1 -> FavoriteButton(
                                                    isFavorite = false,
                                                    nonFavoriteIcon = {
                                                        Icon(Icons.Default.MoreVert, null)
                                                    },
                                                ) {
                                                    model.loadReply(comment).join()
                                                }

                                                0 -> FavoriteButton(
                                                    isFavorite = false,
                                                    nonFavoriteIcon = {
                                                        Icon(Icons.Default.Edit, null)
                                                    },
                                                ) {
                                                    model.loadReply(comment).join()
                                                }

                                                1 -> FavoriteButton(
                                                    isFavorite = false,
                                                    nonFavoriteIcon = {
                                                        Icon(Icons.Default.Close, null)
                                                    },
                                                ) {
                                                    model.clearReply().join()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(modifier = Modifier.padding(5.dp)) {
                                    if (comment.stamp == null) {
                                        CommentText(comment = comment.comment, emojis = medias.emojis)
                                    } else {
                                        AsyncImage(
                                            model = comment.stamp!!.url,
                                            modifier = Modifier.size(100.dp),
                                            contentDescription = null,
                                        )
                                    }
                                }

                                if (state is CommentViewState.Success.HasReply && state.target.id == comment.id) {
                                    val reply = state.reply.collectAsLazyPagingItems()
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        for (i in 0..<reply.itemCount) {
                                            val item = reply[i]!!
                                            ListItem(
                                                headlineContent = {
                                                    Text(item.user.name, style = MaterialTheme.typography.labelSmall)
                                                },
                                                leadingContent = {
                                                    AsyncImage(
                                                        model = item.user.profileImageUrls.content,
                                                        modifier = Modifier.size(25.dp)
                                                            .clickable { stack += AuthorRoute(item.user.id) },
                                                        contentDescription = null,
                                                    )
                                                },
                                                supportingContent = {
                                                    if (item.stamp == null) {
                                                        CommentText(comment = comment.comment, emojis = medias.emojis)
                                                    } else {
                                                        AsyncImage(
                                                            model = item.stamp!!.url,
                                                            modifier = Modifier.size(80.dp),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        if (!data.loadState.isIdle) {
                                            Loading()
                                        } else {
                                            Text(
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(ButtonDefaults.TextButtonContentPadding),
                                                text = stringResource(Res.string.no_more_data),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
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
                        adapter = rememberScrollbarAdapter(scroll),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
                    )

                    BackToTopOrRefreshButton(
                        isNotInTop = scroll.canScrollBackward,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        onBackToTop = { scroll.animateScrollToItem(0) },
                        onRefresh = {
                            isRefresh = true
                            model.refresh()
                            data.awaitNextState()
                            isRefresh = false
                        },
                    )
                }

                var text by remember {
                    mutableStateOf("")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(5.dp),
                    label = {
                        AnimatedContent(
                            state,
                            transitionSpec = {
                                (fadeIn() + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up)) togetherWith (
                                    fadeOut() +
                                        slideOutOfContainer(
                                            AnimatedContentTransitionScope.SlideDirection.Up,
                                        )
                                    )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            when (it) {
                                is CommentViewState.Success.HasReply -> {
                                    Text(stringResource(Res.string.reply_for, it.target.user.name))
                                }

                                else -> {
                                    Text(stringResource(Res.string.comment))
                                }
                            }
                        }
                    },
                    leadingIcon = {
                        AnimatedVisibility((state as? CommentViewState.Success.HasReply)?.target != null) {
                            IconButton(
                                onClick = {
                                    model.clearReply()
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                            }
                        }
                    },
                    trailingIcon = {
                        Row {
                            var sheet by remember {
                                mutableStateOf(false)
                            }
                            IconButton(
                                onClick = {
                                    sheet = true
                                },
                            ) {
                                Icon(Icons.Default.Face, null)
                            }
                            IconButton(
                                onClick = {
                                    model.sendComment(text)
                                    text = ""
                                },
                                enabled = text.isNotBlank(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null)
                            }

                            /**
                             * https://s.pximg.net/soy/pixiv-web-next/_next/static/chunks/87525-06fbd0afc12d9863.js
                             * 79036: (e, t, n) => {
                             *         "use strict";
                             *         n.d(t, {
                             *             RI: () => o,
                             *             e4: () => l,
                             *             ub: () => s
                             *         });
                             *         let i = "https://s.pximg.net/common/images/"
                             *           , o = Object.fromEntries([r(101, "normal"), r(102, "surprise"), r(103, "serious"), r(104, "heaven"), r(105, "happy"), r(106, "excited"), r(107, "sing"), r(108, "cry"), r(201, "normal2"), r(202, "shame2"), r(203, "love2"), r(204, "interesting2"), r(205, "blush2"), r(206, "fire2"), r(207, "angry2"), r(208, "shine2"), r(209, "panic2"), r(301, "normal3"), r(302, "satisfaction3"), r(303, "surprise3"), r(304, "smile3"), r(305, "shock3"), r(306, "gaze3"), r(307, "wink3"), r(308, "happy3"), r(309, "excited3"), r(310, "love3"), r(401, "normal4"), r(402, "surprise4"), r(403, "serious4"), r(404, "love4"), r(405, "shine4"), r(406, "sweat4"), r(407, "shame4"), r(408, "sleep4"), r(501, "heart"), r(502, "teardrop"), r(503, "star")])
                             *           , l = Object.fromEntries([s(301), s(302), s(303), s(304), s(305), s(306), s(307), s(308), s(309), s(310), s(401), s(402), s(403), s(404), s(405), s(406), s(407), s(408), s(409), s(410), s(201), s(202), s(203), s(204), s(205), s(206), s(207), s(208), s(209), s(210), s(101), s(102), s(103), s(104), s(105), s(106), s(107), s(108), s(109), s(110)]);
                             *         function r(e, t) {
                             *             return [t, {
                             *                 id: e,
                             *                 name: "(".concat(t, ")"),
                             *                 imageUrl: new URL("emoji/".concat(e, ".png"),i).href
                             *             }]
                             *         }
                             *         function s(e) {
                             *             return ["".concat(e), {
                             *                 id: e,
                             *                 imageUrl: new URL("stamp/generated-stamps/".concat(e, "_s.jpg"),i).href
                             *             }]
                             *         }
                             *     }
                             */

                            if (sheet) {
                                ModalBottomSheet(onDismissRequest = { sheet = false }) {
                                    var index by remember { mutableStateOf(0) }

                                    val configuration = with(LocalDensity.current) {
                                        LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
                                    }

                                    SecondaryTabRow(
                                        selectedTabIndex = index,
                                        tabs = {
                                            Tab(
                                                selected = index == 0,
                                                onClick = { index = 0 },
                                                text = { Text(stringResource(Res.string.emoji)) },
                                            )
                                            Tab(
                                                selected = index == 1,
                                                onClick = { index = 1 },
                                                text = { Text(stringResource(Res.string.stamp)) },
                                            )
                                        },
                                    )

                                    Text(
                                        if (index == 0) stringResource(Res.string.emoji_desc) else stringResource(Res.string.stamp_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    )

                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = {
                                            text = it
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    model.sendComment(text)
                                                    text = ""
                                                    sheet = false
                                                },
                                                enabled = text.isNotBlank(),
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(5.dp),
                                    )

                                    key(index) {
                                        val size = if (index == 0) 48.dp else 64.dp
                                        LazyVerticalStaggeredGrid(
                                            columns = StaggeredGridCells.Adaptive(size),
                                            modifier = Modifier.height(configuration).padding(size / 4),
                                            contentPadding = PaddingValues(size / 4),
                                        ) {
                                            if (index == 0) {
                                                // https://s.pximg.net/common/images/emoji/303.png
                                                items(medias.emojis.toList()) { (name, code) ->
                                                    AsyncImage(
                                                        model = "https://s.pximg.net/common/images/emoji/$code.png",
                                                        contentDescription = "emoji: $name",
                                                        modifier = Modifier.size(48.dp).clickable {
                                                            text += "($name)"
                                                        },
                                                    )
                                                }
                                            }

                                            if (index == 1) {
                                                // https://s.pximg.net/common/images/stamp/generated-stamps/304_s.jpg?20180605
                                                items(medias.stamps) { code ->
                                                    AsyncImage(
                                                        model = "https://s.pximg.net/common/images/stamp/generated-stamps/${code}_s.jpg?20180605",
                                                        contentDescription = "stamp: $code",
                                                        modifier = Modifier.size(64.dp).clickable {
                                                            model.sendComment(code.toLong())
                                                            text = ""
                                                            sheet = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

private val pattern = Regex("\\(([^)]+)\\)")

@Composable
private fun CommentText(modifier: Modifier = Modifier, comment: String, emojis: Map<String, Int>) {
    val style = LocalTextStyle.current
    val state by produceState(AnnotatedString("") to emptyMap<String, InlineTextContent>()) {
        val inlineContentMap = mutableMapOf<String, InlineTextContent>()
        val annotated = buildAnnotatedString {
            var lastIndex = 0
            pattern.findAll(comment).forEachIndexed { index, matchResult ->
                append(comment.substring(lastIndex, matchResult.range.first))
                val key = matchResult.groupValues[1]
                val emojiCode = emojis[key]
                if (emojiCode != null) {
                    val inlineId = "emoji_$index"
                    appendInlineContent(inlineId, "[$key]")
                    inlineContentMap[inlineId] = InlineTextContent(
                        Placeholder(
                            width = style.fontSize * 1.1,
                            height = style.fontSize,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                    ) {
                        val url = "https://s.pximg.net/common/images/emoji/$emojiCode.png"
                        AsyncImage(
                            model = url,
                            contentDescription = key,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    append(matchResult.value)
                }
                lastIndex = matchResult.range.last + 1
            }
            if (lastIndex < comment.length) {
                append(comment.substring(lastIndex))
            }
        }

        value = annotated to inlineContentMap
    }

    // 3. 渲染 Text
    SelectionContainer {
        Text(
            text = state.first,
            inlineContent = state.second,
            modifier = modifier,
        )
    }
}

@Serializable
private data class CommentMedia(
    val emojis: Map<String, Int>,
    val stamps: List<Int>,
)
