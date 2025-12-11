package top.kagg886.pmf.ui.route.main.detail.illust

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImagePainter.State
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.dokar.chiptextfield.util.runIf
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.buffer
import okio.use
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.filepicker.FilePicker
import top.kagg886.filepicker.openFileSaver
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.IllustImagesType
import top.kagg886.pixko.module.illust.get
import top.kagg886.pixko.module.illust.getIllustDetail
import top.kagg886.pixko.module.search.SearchSort
import top.kagg886.pixko.module.search.SearchTarget
import top.kagg886.pmf.*
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.Platform
import top.kagg886.pmf.backend.SystemConfig
import top.kagg886.pmf.backend.currentPlatform
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.backend.useTempFile
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.*
import top.kagg886.pmf.ui.component.dialog.TagFavoriteDialog
import top.kagg886.pmf.ui.component.icon.Copy
import top.kagg886.pmf.ui.component.icon.Download
import top.kagg886.pmf.ui.component.icon.Save
import top.kagg886.pmf.ui.component.icon.View
import top.kagg886.pmf.ui.component.scroll.VerticalScrollbar
import top.kagg886.pmf.ui.component.scroll.rememberScrollbarAdapter
import top.kagg886.pmf.ui.route.main.download.DownloadScreenModel
import top.kagg886.pmf.ui.route.main.search.v2.SearchResultRoute
import top.kagg886.pmf.ui.util.*
import top.kagg886.pmf.util.*

private val cache = SystemConfig.getConfig("cache")

class TodoSerializer : KSerializer<List<Illust>> {
    override val descriptor = PrimitiveSerialDescriptor("Todo", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: List<Illust>) {
        val json = Json.encodeToString(value)
        val uuid = Uuid.random()
        cache["$uuid"] = json
        encoder.encodeSerializableValue(Uuid.serializer(), uuid)
    }

    override fun deserialize(decoder: Decoder): List<Illust> {
        val uuid = decoder.decodeSerializableValue(Uuid.serializer())
        val json: String = cache["$uuid"]!!
        return Json.decodeFromString(json)
    }
}

@Serializable
data class IllustDetailRoute(
    val index: Int,
    @Serializable(with = TodoSerializer::class)
    val todos: List<Illust>,
) : NavKey

fun IllustDetailRoute(illust: Illust) = IllustDetailRoute(0, listOf(illust))

@Composable
fun IllustDetailScreen(route: IllustDetailRoute) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val current = route.todos[route.index]
    val todos = route.todos
    HorizontalPager(state = rememberPagerState(initialPage = todos.indexOf(current)) { todos.size }) { index ->
        val illust = todos[index]
        val model = koinViewModel<IllustDetailViewModel>(key = "${illust.id}") {
            parametersOf(illust)
        }
        val state by model.collectAsState()
        val host = LocalSnackBarHost.current
        val nav = LocalNavBackStack.current
        model.collectSideEffect {
            when (it) {
                is IllustDetailSideEffect.Toast -> host.showSnackbar(it.msg)
                is IllustDetailSideEffect.NavigateBack -> nav.removeLastOrNullWorkaround()
            }
        }
        Box(Modifier.width(maxWidth).height(maxHeight)) {
            IllustDetailScreenContent(state, model)
        }
    }
}

@Composable
private fun IllustDetailScreenContent(
    state: IllustDetailViewState,
    model: IllustDetailViewModel,
) {
    when (state) {
        IllustDetailViewState.Error -> {
            ErrorPage(text = stringResource(Res.string.error)) {
                model.load()
            }
        }

        is IllustDetailViewState.Loading -> {
            val text by state.data.collectAsState()
            Loading(text = text)
        }

        is IllustDetailViewState.Success -> {
            if (useWideScreenMode) {
                WideScreenIllustDetail(state, model)
                return
            }
            IllustDetail(state, model)
        }
    }
}

@Composable
private fun IllustTopAppBar(
    illust: Illust,
    inViewLater: Boolean,
    onCommentPanelBtnClick: () -> Unit = {},
    onOriginImageRequest: () -> Unit = {},
    onViewLaterBtnClick: (Boolean) -> Unit = {},
    onBlackRequest: () -> Unit = {},
) {
    val stack = LocalNavBackStack.current
    TopAppBar(
        title = { Text(text = stringResource(Res.string.image_details)) },
        navigationIcon = {
            IconButton(onClick = { stack.removeLastOrNullWorkaround() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
        },
        actions = {
            var enabled by remember { mutableStateOf(false) }
            IconButton(
                onClick = { enabled = true },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(Icons.Default.Menu, null)
            }
            DropdownMenu(
                expanded = enabled,
                onDismissRequest = { enabled = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.open_in_browser)) },
                    onClick = {
                        openBrowser("https://pixiv.net/artworks/${illust.id}")
                        enabled = false
                    },
                )

                if (inViewLater) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.remove_watch_later)) },
                        onClick = {
                            onViewLaterBtnClick(false)
                            enabled = false
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.add_watch_later)) },
                        onClick = {
                            onViewLaterBtnClick(true)
                            enabled = false
                        },
                    )
                }

                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.filter_add, stringResource(Res.string.user))) },
                    onClick = {
                        onBlackRequest()
                        enabled = false
                    },
                )

                val clip = LocalClipboard.current
                val scope = rememberCoroutineScope()
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.open_in_clipboard)) },
                    onClick = {
                        scope.launch { clip.setText("https://pixiv.net/artworks/${illust.id}") }
                        enabled = false
                    },
                )

                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.show_original_image)) },
                    onClick = onOriginImageRequest,
                )
            }
            if (currentPlatform !is Platform.Desktop) {
                IconButton(
                    onClick = onCommentPanelBtnClick,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(Icons.Default.Edit, null)
                }
            }
        },
    )
}

@Composable
private fun WideScreenIllustDetail(
    state: IllustDetailViewState.Success,
    model: IllustDetailViewModel,
) {
    Scaffold(
        topBar = {
            IllustTopAppBar(
                illust = state.illust,
                inViewLater = state.itemInViewLater,
                onCommentPanelBtnClick = {},
                onOriginImageRequest = {
                    model.toggleOrigin()
                },
                onViewLaterBtnClick = {
                    if (it) model.addViewLater() else model.removeViewLater()
                },
                onBlackRequest = {
                    model.black()
                },
            )
        },
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(it)) {
            Box(Modifier.fillMaxWidth(0.7f).fillMaxHeight()) {
                IllustPreview(state, model) {
                    model.toggleOrigin()
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                IllustComment(state.illust)
            }
        }
    }
}

@Composable
private fun IllustDetail(
    state: IllustDetailViewState.Success,
    model: IllustDetailViewModel,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    SupportRTLModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                IllustComment(state.illust)
            }
        },
        rtlLayout = true,
        drawerState = drawerState,
        // 滑动展开comment模式且comment panel open时，需要启用手势滑动关闭
        gesturesEnabled = AppConfig.illustDetailOpenFor == AppConfig.DetailSlideOpenFor.OpenComment || drawerState.isOpen,
    ) {
        Scaffold(
            topBar = {
                IllustTopAppBar(
                    illust = state.illust,
                    inViewLater = state.itemInViewLater,
                    onCommentPanelBtnClick = {
                        scope.launch {
                            if (drawerState.isOpen) {
                                drawerState.close()
                            } else {
                                drawerState.open()
                            }
                        }
                    },
                    onOriginImageRequest = {
                        model.toggleOrigin()
                    },
                    onViewLaterBtnClick = {
                        if (it) model.addViewLater() else model.removeViewLater()
                    },
                    onBlackRequest = {
                        model.black()
                    },
                )
            },
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(it)) {
                IllustPreview(state, model) {
                    model.toggleOrigin()
                }
            }
        }
    }
}

@Composable
private fun IllustPreview(
    state: IllustDetailViewState.Success,
    model: IllustDetailViewModel,
    onOriginImageRequest: () -> Unit,
) {
    val stack = LocalNavBackStack.current
    val illust = state.illust
    Box(modifier = Modifier.fillMaxSize()) {
        val scroll = rememberLazyListState()

        val controller = remember {
            keyboardScrollerController(scroll) {
                scroll.layoutInfo.viewportSize.height.toFloat()
            }
        }

        KeyListenerFromGlobalPipe(controller)

        var preview by remember { mutableStateOf(false) }
        var startIndex by remember { mutableStateOf(0) }
        if (preview) {
            ImagePreviewer(
                onDismiss = { preview = false },
                data = state.data, // preview should be show all
                modifier = Modifier.fillMaxSize(),
                startIndex = startIndex,
            )
        }

        var expand by remember { mutableStateOf(AppConfig.illustDetailsShowAll) }
        val img by remember(state.data.hashCode(), expand) {
            mutableStateOf(state.data.let { if (!expand) it.take(3) else it })
        }
        LazyColumn(
            state = scroll,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(img, key = { i, _ -> i }) { i, uri ->
                var ratio by remember { mutableStateOf(illust.width.toFloat() / illust.height) }
                SubcomposeAsyncImage(
                    model = uri,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                    onState = { state: State ->
                        if (state is State.Success) {
                            val image = state.result.image
                            ratio = image.width.toFloat() / image.height.toFloat()
                        }
                    },
                    contentDescription = null,
                ) {
                    var menuOffsetPx by remember {
                        mutableStateOf(DpOffset.Zero)
                    }
                    var menuExpanded by remember {
                        mutableStateOf(false)
                    }
                    val density = LocalDensity.current
                    val state by painter.state.collectAsState()
                    when (val s = state) {
                        is State.Success -> SubcomposeAsyncImageContent(
                            modifier = Modifier
                                .runIf(currentPlatform is Platform.Desktop) {
                                    pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.type == PointerEventType.Press) {
                                                    val change = event.changes.firstOrNull() ?: continue
                                                    if (event.buttons.isSecondaryPressed) {
                                                        change.consume()
                                                        // 保存鼠标位置（像素）
                                                        menuOffsetPx = with(density) {
                                                            DpOffset(
                                                                change.position.x.toDp(),
                                                                change.position.y.toDp(),
                                                            )
                                                        }
                                                        menuExpanded = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .clickable {
                                    startIndex = i
                                    preview = true
                                },
                        )

                        is State.Loading -> Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        is State.Error -> ErrorPage(
                            modifier = Modifier.align(Alignment.Center),
                            text = s.result.throwable.message ?: "Unknown Error",
                            onClick = { model.load() },
                        )

                        else -> Unit
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        offset = menuOffsetPx,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        val scope = rememberCoroutineScope()
                        val snack = LocalSnackBarHost.current
                        val ctx = LocalPlatformContext.current
                        if (currentPlatform is Platform.Desktop) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(Res.string.copy_to_clipboard))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Copy,
                                        null,
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        val bytes = ctx.readBytes(uri.toString())
                                        if (bytes == null) {
                                            snack.showSnackbar(getString(Res.string.file_was_downloading))
                                        } else {
                                            runCatching {
                                                copyImageToClipboard(bytes)
                                            }.onSuccess {
                                                snack.showSnackbar(getString(Res.string.copy_to_clipboard_success))
                                            }.onFailure {
                                                logger.w("copy image to clipboard failed", it)
                                                snack.showSnackbar(getString(Res.string.copy_to_clipboard_failed))
                                            }
                                        }
                                        menuExpanded = false
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(Res.string.save))
                            },
                            leadingIcon = {
                                Icon(Save, null)
                            },
                            onClick = {
                                scope.launch {
                                    val key = uri.toString()
                                    val isGif = key.startsWith(UGOIRA_SCHEME)
                                    val bytes = ctx.readBytes(key)
                                    if (bytes == null) {
                                        snack.showSnackbar(getString(Res.string.file_was_downloading))
                                    } else {
                                        val platformFile = FilePicker.openFileSaver(
                                            suggestedName = Uuid.random().toHexString(),
                                            extension = if (isGif) "gif" else "png",
                                        )
                                        platformFile?.buffer()?.use { buf -> buf.write(bytes) }
                                    }
                                    menuExpanded = false
                                }
                            },
                        )
                        if (currentPlatform is Platform.Android) {
                            SettingsMenuLink(
                                title = {
                                    Text(stringResource(Res.string.share))
                                },
                                icon = {
                                    Icon(Icons.Default.Share, null)
                                },
                                onClick = {
                                    scope.launch {
                                        val key = uri.toString()
                                        val isGif = key.startsWith(UGOIRA_SCHEME)
                                        val bytes = ctx.readBytes(key)
                                        if (bytes == null) {
                                            getString(Res.string.file_was_downloading)
                                        } else {
                                            val source = Buffer().write(bytes)
                                            useTempFile { tmp ->
                                                tmp.sink().buffer().use { source.transfer(it) }
                                                if (isGif) {
                                                    shareFile(
                                                        tmp,
                                                        name = "${Uuid.random().toHexString()}.gif",
                                                        mime = "image/gif",
                                                    )
                                                } else {
                                                    shareFile(tmp, mime = "image/*")
                                                }
                                            }
                                        }
                                    }
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            if (illust.contentImages.size > 3 && !expand) {
                item(key = "expand") {
                    TextButton(
                        onClick = { expand = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(Res.string.expand_more),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item(key = "author") {
                AuthorCard(
                    modifier = Modifier.fillMaxWidth(),
                    user = illust.user,
                    onFavoritePrivateClick = { model.followUser(true).join() },
                ) {
                    if (it) {
                        model.followUser().join()
                    } else {
                        model.unFollowUser().join()
                    }
                }
            }
            item(key = "info") {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    val clipboard = LocalClipboard.current
                    val theme = MaterialTheme.colorScheme
                    SupportListItem(
                        overlineContent = {
                            Text(
                                text = buildAnnotatedString {
                                    withClickable(theme, illust.id.toString()) {
                                        model.intent {
                                            clipboard.setText(
                                                illust.id.toString(),
                                            )
                                            postSideEffect(
                                                IllustDetailSideEffect.Toast(
                                                    getString(Res.string.copy_pid),
                                                ),
                                            )
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = buildAnnotatedString {
                                    withClickable(theme, illust.title) {
                                        model.intent {
                                            clipboard.setText(illust.title)
                                            postSideEffect(
                                                IllustDetailSideEffect.Toast(
                                                    getString(Res.string.copy_title_success),
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        },
                        trailingContent = {
                            Row(
                                Modifier.size(120.dp, 68.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = View,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Text(illust.totalView.toString())
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    var betterFavoriteDialog by remember {
                                        mutableStateOf(false)
                                    }
                                    if (betterFavoriteDialog) {
                                        TagFavoriteDialog(
                                            tags = illust.tags,
                                            title = { Text(stringResource(Res.string.bookmark_extra_options)) },
                                            confirm = { tags, publicity ->
                                                model.likeIllust(publicity, tags).join()
                                                betterFavoriteDialog = false
                                            },
                                            cancel = {
                                                betterFavoriteDialog = false
                                            },
                                        )
                                    }

                                    val illust by illustRouter.collectLatest(illust)
                                    FavoriteButton(
                                        isFavorite = illust.isBookMarked,
                                        modifier = Modifier.size(30.dp),
                                        onDoubleClick = { betterFavoriteDialog = true },
                                    ) {
                                        if (it == FavoriteState.Favorite) {
                                            model.likeIllust().join()
                                            return@FavoriteButton
                                        }
                                        if (it == FavoriteState.NotFavorite) {
                                            model.disLikeIllust().join()
                                            return@FavoriteButton
                                        }
                                    }
                                    RollingNumber(illust.totalBookmarks)
                                }
                                val downloadModel = koinViewModel<DownloadScreenModel>()
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(
                                        onClick = {
                                            downloadModel.startIllustDownload(illust)
                                        },
                                        enabled = illust.contentImages[IllustImagesType.ORIGIN] != null,
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(Download, null)
                                    }
                                    Text(stringResource(Res.string.download))
                                }
                            }
                        },
                        supportingContent = {
                            SelectionContainer {
                                HTMLRichText(
                                    html = illust.caption.ifEmpty { stringResource(Res.string.no_description) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ListItemDefaults.colors().supportingTextColor,
                                )
                            }
                        },
                    )
                }
            }
            item(key = "tags") {
                OutlinedCard {
                    ListItem(
                        overlineContent = {
                            Text(stringResource(Res.string.tags))
                        },
                        headlineContent = {
                            FlowRow {
                                val stack = LocalNavBackStack.current
                                for (tag in illust.tags) {
                                    AssistChip(
                                        modifier = Modifier.padding(4.dp),
                                        onClick = {
                                            stack += SearchResultRoute(
                                                keyword = listOf(tag.name),
                                                sort = SearchSort.DATE_DESC,
                                                target = SearchTarget.PARTIAL_MATCH_FOR_TAGS,
                                            )
                                        },
                                        label = {
                                            Column {
                                                Text(
                                                    tag.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                                tag.translatedName?.let {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            item(key = "publish_date") {
                OutlinedCard {
                    ListItem(
                        overlineContent = {
                            Text(stringResource(Res.string.publish_date))
                        },
                        headlineContent = {
                            Text(
                                illust.createTime.toReadableString(),
                            )
                        },
                    )
                }
            }

            item(key = "similar") {
                OutlinedCard {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(Res.string.find_similar_illust),
                            )
                        },
                        modifier = Modifier.clickable {
                            stack += IllustSimilarRoute(illust.id.toLong())
                        },
                    )
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun IllustComment(illust: Illust) {
    val model = koinViewModel<IllustCommentViewModel>(key = "illust_comment_${illust.id}") {
        parametersOf(illust.id.toLong())
    }

    CommentPanel(
        model = model,
        modifier = Modifier.fillMaxSize(),
    )
}

@Serializable
data class IllustDetailPreFetchRoute(val id: Long) : NavKey

@Composable
fun IllustDetailPreFetchScreen(route: IllustDetailPreFetchRoute) {
    val id = route.id
    val client = remember { PixivConfig.newAccountFromConfig() }
    val stack = LocalNavBackStack.current
    val snack = LocalSnackBarHost.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { client.getIllustDetail(id) }.fold(
                {
                    stack[stack.size - 1] = IllustDetailRoute(it)
                },
                { snack.showSnackbar(getString(Res.string.cant_load_illust, id)) },
            )
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Loading()
        IconButton(
            onClick = { stack.removeLastOrNullWorkaround() },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
        }
    }
}
