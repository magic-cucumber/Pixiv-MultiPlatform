# 项目开发规范

本文件规定本项目的开发方式，所有参与开发的人员与智能体必须遵守。

## 国际化文本

- 所有用户可见文本都必须使用国际化资源；不要在界面代码中直接写文本。
- 按页面在 `sharedUI/i18n/src/i18n/` 中新增或维护 YAML 文件：目录树参照 `sharedUI/src/commonMain/kotlin/top/kagg886/pmf/ui/screen/` 的页面层级组织，每个页面使用独立文件，YAML 文件名必须与对应的 screen 名称一致。例如 `ui/screen/login/screen.kt` 对应 `i18n/login.yaml`，`ui/screen/main/home/screen.kt` 对应 `i18n/main/home.yaml`；新增或移动页面时，必须同步新增或移动对应的 YAML 文件；
- 不要将多个页面的文案集中到巨型国际化文件中。
- 仅在确实由多个页面共享时，才放入按共享功能命名的文件。
- 每个文本使用稳定且语义明确的标识，并同时提供英文与简体中文。

  ```yaml
  feature_action:
    en_us: "Example action"
    zh_cn: "示例操作"
  ```

- 同一个标识只能定义一次。新增语言时，在同一文本下补充其译文；不要另建一套重复的文本标识。
- 界面中通过资源标识获取文案，而不是自行判断语言或维护文案映射。

  ```kotlin
  Text(text = stringResource(Lang.string.feature_action))
  ```

## 页面结构

每个功能页面使用同一目录下的 `route.kt`、`model.kt` 与 `screen.kt` 组织。简单的叶子页面没有额外的页面结构时，可以只保留 `screen.kt`。

### `route.kt`：页面注册

- 注册该功能的页面或子页面；页面的路由标识定义在对应的 `screen.kt`。
- 页面容器的 `route.kt` 只组合其直接子页面；根目录的 `route.kt` 只组合应用的一级页面容器与独立页面。
- 当前应用从根页面容器开始，注册 Welcome、Login，并将 Main 的页面结构作为子页面接入：

  ```kotlin
  val ApplicationGraph = createNavGraph {
      route(parent = RootRoute, startDestination = WelcomeRoute, content = ::RootScreen) {
          destination<WelcomeRoute> { WelcomeScreen() }
          destination<LoginRoute> { LoginScreen() }
          route<MainRoute>(MainRoute, HomeRoute, ::MainScreen, MainRouteGraph)
      }
  }
  ```

- Main 的 `route.kt` 继续注册自己的直接页面；新功能按此方式向上组合，不要将所有页面平铺在根注册处。

  ```kotlin
  val MainRouteGraph = {
      destination<HomeRoute> { HomeScreen() }
  }
  ```

### `model.kt`：页面状态与行为

- 只负责页面状态、用户操作和一次性结果，不负责绘制界面。
- 将界面需要展示的数据放入状态；将完成跳转、提示等一次性结果放入效果，由 `screen.kt` 接收后执行。

  ```kotlin
  data class FeatureState(val loading: Boolean = true)

  sealed interface FeatureEffect {
      data object NavigateToNext : FeatureEffect
  }
  ```

### `screen.kt`：界面与交互

- 声明页面路由标识和页面入口，负责绘制界面、读取状态，并将用户操作交给 `model.kt`。
- 每个 `screen.kt` 只能声明一个 route class，并且只能有一个与该 route class 绑定的公开 screen 入口；文件中其他用于拆分状态、布局或预览的 screen/content composable 必须使用 `private` 修饰，不得再绑定路由。
- 必要时可以为页面添加 `@Preview` 注解，便于在 IDE 中复现页面状态；Preview 仅用于预览或复现，不新增 route class，也不作为额外的路由入口。
- 所有对话框都必须声明独立的 route，并通过 `navigate3` 的 `dialog` 接口注册；对话框应使用独立的 `screen.kt`、route class 和页面入口，不得在普通页面的 screen/content composable 中直接创建 `Dialog` 或 `AlertDialog` 作为临时 UI。
- 在此处接收一次性效果并完成跳转。若离开的是临时页面且不应返回，先移除该页面，再进入下一页。

  ```kotlin
  model.collectSideEffect { effect ->
      if (effect == FeatureEffect.NavigateToNext) {
          nav.removeBackStack(FeatureRoute)
          nav.navigate(NextRoute)
      }
  }
  ```

- 容器页面负责显示其子页面；子页面只负责自身内容。容器共享的状态可供其子页面使用，页面自身状态不得跨功能长期复用。

## 组件设计及封装规范

- 页面的公开入口只负责收集数据、判断页面状态、获取导航等环境依赖，并将整理后的状态、数据和事件回调传给私有 `Content`；具体界面不得堆叠在公开入口中。

  ```kotlin
  @Composable
  fun FeatureScreen(model: FeatureViewModel) {
      val state by model.state.collectAsState()
      FeatureContent(state = state, onRetry = model::retry) // 公开入口只做状态调度
  }
  ```

- 私有 `Content` 应通过参数接收显示所需的状态、数据和回调，不直接依赖页面 ViewModel 或导航环境，使其可以独立预览和复用。

  ```kotlin
  @Composable
  private fun FeatureContent(state: FeatureState, onRetry: () -> Unit) {
      // 只使用参数，不在这里获取 ViewModel 或 NavController
      if (state.failed) ErrorContent(onRetry = onRetry)
  }
  ```

- 页面存在加载、成功、错误等互斥状态时，应定义明确的页面状态，并由统一的状态容器负责切换；各状态的具体界面应拆分为私有 Content composable。

  ```kotlin
  private enum class ScreenState { Loading, Success, Error }

  AnimatedContent(targetState = screenState) { state ->
      when (state) { // 这里只分发，各状态 UI 独立封装
          ScreenState.Loading -> LoadingContent()
          ScreenState.Success -> SuccessContent()
          ScreenState.Error -> ErrorContent()
      }
  }
  ```

- 页面状态切换应使用过渡动画。默认使用淡入淡出；只有业务语义明确且布局变化适合时，才为特定状态转换增加展开、收缩等尺寸动画，不要让所有状态共用复杂转场。

  ```kotlin
  transitionSpec = {
      if (initialState == ScreenState.Loading && targetState == ScreenState.Success) {
          (fadeIn() + expandIn()) togetherWith (fadeOut() + shrinkOut()) // 特定状态对
      } else {
          fadeIn() togetherWith fadeOut() // 其余状态使用默认淡入淡出
      }
  }
  ```

- 首次加载且尚无可展示内容时，可以显示整页加载或错误状态；已有内容的刷新不应替换整个页面，应保留当前内容，并使用页面内的轻量进度提示反馈刷新状态。

  ```kotlin
  val screenState = when {
      items.isEmpty() && refreshLoading -> ScreenState.Loading // 仅空内容时占满页面
      items.isEmpty() && refreshFailed -> ScreenState.Error
      else -> ScreenState.Success // 已有内容时继续显示
  }
  AnimatedVisibility(visible = items.isNotEmpty() && refreshLoading) {
      LinearProgressIndicator(Modifier.fillMaxWidth()) // 页内刷新反馈
  }
  ```

- 状态容器应占满页面可用区域，并为不同状态提供稳定的对齐基准，避免状态切换时产生无意义的位置跳动。

  ```kotlin
  AnimatedContent(
      targetState = screenState,
      modifier = Modifier.fillMaxSize(), // 各状态使用相同页面范围
      contentAlignment = Alignment.Center, // 固定切换时的对齐基准
  ) { state -> /* ... */ }
  ```

- 列表或网格页面应使用同一个间距参数推导内容边距、横向间距和纵向间距，不要在页面各处分别硬编码；涉及水平方向时应兼容当前布局方向。

  ```kotlin
  val direction = LocalLayoutDirection.current
  val horizontalSpacing = (
      itemPadding.calculateStartPadding(direction) +
          itemPadding.calculateEndPadding(direction)
  ) / 2 // 间距统一从 itemPadding 推导
  LazyVerticalGrid(contentPadding = itemPadding, horizontalArrangement = Arrangement.spacedBy(horizontalSpacing))
  ```

- 滚动条应与对应的列表或网格共享同一个滚动状态，并通过适配器连接；需要覆盖在内容侧边时，使用同一层叠容器对齐，不维护独立滚动状态。

  ```kotlin
  val listState = rememberLazyListState()
  Box {
      LazyColumn(state = listState) { /* ... */ }
      VerticalScrollbar(
          adapter = rememberScrollbarAdapter(listState), // 与列表共享状态
          modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
      )
  }
  ```

- Lazy 列表或网格中的数据项应提供稳定且唯一的 key；分页数据尚未就绪时，应显示尺寸稳定的占位内容，避免布局突然塌陷或重排。

  ```kotlin
  items(
      count = pagingItems.itemCount,
      key = { index -> pagingItems.peek(index)?.id ?: "placeholder-$index" }, // 稳定 key
  ) { index ->
      pagingItems[index]?.let { ItemContent(it) } ?: ItemPlaceholder() // 保留条目尺寸
  }
  ```

- 依赖异步内容才能操作的浮层控件，应在依赖内容准备完成后再显示，并使用可见性动画平滑出现或消失。

  ```kotlin
  AnimatedVisibility(
      visible = imageLoaded, // 图片成功后才允许显示操作
      enter = fadeIn(),
      exit = fadeOut(),
  ) {
      ItemActionButton()
  }
  ```

- 异步操作开始后，应立即切换对应控件的加载状态并阻止重复触发；无论成功或失败，都应在操作结束时恢复加载状态。Lazy 容器中的局部状态应以数据项的稳定标识为记忆键，防止复用时串到其他条目。

  ```kotlin
  var actionLoading by remember(item.id) { mutableStateOf(false) } // 状态绑定条目
  fun onAction() {
      if (actionLoading) return // 阻止重复触发
      scope.launch {
          actionLoading = true
          try { submit(item) } finally { actionLoading = false } // 所有结果都恢复状态
      }
  }
  ```

- 优先复用项目已有的加载、错误、图片、滚动条和带进度操作组件，不在页面内重复实现同类组件。

  ```kotlin
  EmptyScreen(/* 项目统一错误态 */)
  ProgressableImage(model = imageUrl, contentDescription = description)
  LoadingIconButton(state = buttonState, onClick = onClick) { Icon(/* ... */) }
  ```

- 应为加载、成功、错误等主要状态分别提供私有 Preview；预览调用私有 `Content`，不经过真实数据源、导航或业务状态容器。

  ```kotlin
  @Preview(name = "Loading")
  @Composable
  private fun FeatureLoadingPreview() {
      MaterialTheme { FeatureContent(state = PreviewState.Loading, onRetry = {}) } // 直接预览纯 UI
  }
  ```

## 弹窗与蒙层设计

- 按交互的信息量和操作复杂度选择蒙层形态，不要仅凭视觉样式互换组件。
- `dialog` 用于确认、取消、提示等简短信息交互；需要附带表单时，应限制为不超过 2 个轻量操作项，例如开关、按钮或输入框。
- `bottom sheet` 用于需要连续处理 3 个及以上表单项的流程，以及选择器、填写后提交等内容较多的交互；仅有 1～2 个操作项时优先使用 `dialog`，避免为简单任务引入过重的操作面板。
- `drawer` 用于在当前内容旁补充列表型信息，例如详情页中的“猜你喜欢”；不要用它承载主要表单流程或替代普通页面导航。

## 日志

新增 ViewModel 或 Repository 时，必须为线上问题复现补充业务日志。使用 `plugins/logger/processor` 提供的注解生成 logger，不要自行创建无统一 tag 的 logger。

```kotlin
import top.kagg886.pmf.logger.Logger

@Logger // 默认 tag 为类的完整限定名
class FeatureViewModel : ViewModel() {
    fun load() {
        logger.i { "Starting feature data load" }
    }
}
```

- All business log messages must be written in English. Do not mix Chinese or other natural-language text into log messages; keep code identifiers and state names when they improve traceability.
- `@Logger` 只能标注类；processor 会在同包生成该类的 `logger: co.touchlab.kermit.Logger` 扩展属性。需要更短且稳定的 tag 时使用 `@Logger("FeatureViewModel")`；不要将注解放在 `private` 或 `protected` 类及其嵌套类上。
- 日志应描述实际控制流、关键输入的安全摘要、执行结果和状态变化，以便根据日志重建一次业务路径。不得记录 access token、refresh token、密码、Cookie、完整授权 URL、用户隐私数据或其他敏感原文；必要时只记录是否存在、长度、ID 的脱敏形式或错误类型。
- 不为简单的赋值、纯转发、显而易见的空值判断等无诊断价值的代码增加日志。循环、轮询和高频回调须只记录聚合结果、首次/末次事件或状态变化，避免刷屏。

### 等级与覆盖

- `logger.v`：用于较复杂函数和有诊断价值的控制流，记录函数 enter/exit、重要 `if` / `else`、`when` 分支及提前返回的选择。目标是控制流可追踪，而非每一行都打 verbose：某个控制流已经有 `d`、`i`、`w` 或 `e` 日志时，不再重复打 `v`；过于简单的分支也不打 `v`。
- `logger.d`：按函数内可独立判断的功能段分组记录，输出该段的预期、实际结果和是否符合预期。例如请求发起后的响应摘要、缓存读写是否命中、转换后的条目数量与有效性；不要只打印“执行到这里”。
- `logger.i`：记录重要运行状态和可观察的业务决策，尤其是 ViewModel 将要设置的 state、发送的 effect、Repository 选择的数据源或最终采用的结果。
- `logger.w`：仅在发现非预期状态、但准备继续执行 fallback 时使用。必须说明异常原因、受影响的原路径以及即将采用的 fallback，例如 `"Cache parsing failed (type: ...); dropping the cache and falling back to a network request."`。
- `logger.e`：用于非预期退出且会改变/中断程序流程的情况。说明操作、原因和退出结果；有异常时传入 throwable，例如 `logger.e(throwable) { "Loading failed; setting state to Error" }`。被捕获后继续走 fallback 的异常使用 `w`，最终无法继续才使用 `e`。
- `logger.a`：保留作为 silent 级别；除既有基础设施约定外，不在新的 ViewModel 或 Repository 业务路径中使用。

### ViewModel 与 Repository 要求

- ViewModel 至少记录：用户/页面入口触发、关键参数的安全摘要、准备 `reduce` 的 state、准备 `postSideEffect` 的 effect、失败时的 UI 退出状态；异步流程还应记录成功、取消和异常分支。
- Repository 至少记录：调用入口、数据源选择、请求或持久化的结果摘要、数据转换/校验结果、缓存或默认值 fallback、以及会返回给上层的失败类型。不要把同一事件在 Repository 和 ViewModel 以相同内容重复打印；Repository 记录数据层事实，ViewModel 记录 UI 决策。
- 对 `try` / `catch`、空结果、超时、解析失败、缓存失效和提前返回，按实际处理分支补齐相应等级的日志；日志与最终返回值、state 或 effect 必须一致。
