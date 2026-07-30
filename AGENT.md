# 项目开发规范

本文件规定本项目的开发方式，所有参与开发的人员与智能体必须遵守。

## 国际化文本

- 所有用户可见文本都必须使用国际化资源；不要在界面代码中直接写文本。
- 按页面在 `sharedUI/i18n/src/i18n/` 中新增或维护 YAML 文件：每个页面使用独立文件，文件名应体现页面职责（如 `login.yaml`）；
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

## 日志

新增 ViewModel 或 Repository 时，必须为线上问题复现补充业务日志。使用 `plugins/logger/processor` 提供的注解生成 logger，不要自行创建无统一 tag 的 logger。

```kotlin
import top.kagg886.pmf.logger.Logger

@Logger // 默认 tag 为类的完整限定名
class FeatureViewModel : ViewModel() {
    fun load() {
        logger.i { "开始加载功能数据" }
    }
}
```

- `@Logger` 只能标注类；processor 会在同包生成该类的 `logger: co.touchlab.kermit.Logger` 扩展属性。需要更短且稳定的 tag 时使用 `@Logger("FeatureViewModel")`；不要将注解放在 `private` 或 `protected` 类及其嵌套类上。
- 日志应描述实际控制流、关键输入的安全摘要、执行结果和状态变化，以便根据日志重建一次业务路径。不得记录 access token、refresh token、密码、Cookie、完整授权 URL、用户隐私数据或其他敏感原文；必要时只记录是否存在、长度、ID 的脱敏形式或错误类型。
- 不为简单的赋值、纯转发、显而易见的空值判断等无诊断价值的代码增加日志。循环、轮询和高频回调须只记录聚合结果、首次/末次事件或状态变化，避免刷屏。

### 等级与覆盖

- `logger.v`：用于较复杂函数和有诊断价值的控制流，记录函数 enter/exit、重要 `if` / `else`、`when` 分支及提前返回的选择。目标是控制流可追踪，而非每一行都打 verbose：某个控制流已经有 `d`、`i`、`w` 或 `e` 日志时，不再重复打 `v`；过于简单的分支也不打 `v`。
- `logger.d`：按函数内可独立判断的功能段分组记录，输出该段的预期、实际结果和是否符合预期。例如请求发起后的响应摘要、缓存读写是否命中、转换后的条目数量与有效性；不要只打印“执行到这里”。
- `logger.i`：记录重要运行状态和可观察的业务决策，尤其是 ViewModel 将要设置的 state、发送的 effect、Repository 选择的数据源或最终采用的结果。
- `logger.w`：仅在发现非预期状态、但准备继续执行 fallback 时使用。必须说明异常原因、受影响的原路径以及即将采用的 fallback，例如“缓存解析失败（类型：…），丢弃缓存并改为网络请求”。
- `logger.e`：用于非预期退出且会改变/中断程序流程的情况。说明操作、原因和退出结果；有异常时传入 throwable，例如 `logger.e(throwable) { "加载失败，状态将设为 Error" }`。被捕获后继续走 fallback 的异常使用 `w`，最终无法继续才使用 `e`。
- `logger.a`：保留作为 silent 级别；除既有基础设施约定外，不在新的 ViewModel 或 Repository 业务路径中使用。

### ViewModel 与 Repository 要求

- ViewModel 至少记录：用户/页面入口触发、关键参数的安全摘要、准备 `reduce` 的 state、准备 `postSideEffect` 的 effect、失败时的 UI 退出状态；异步流程还应记录成功、取消和异常分支。
- Repository 至少记录：调用入口、数据源选择、请求或持久化的结果摘要、数据转换/校验结果、缓存或默认值 fallback、以及会返回给上层的失败类型。不要把同一事件在 Repository 和 ViewModel 以相同内容重复打印；Repository 记录数据层事实，ViewModel 记录 UI 决策。
- 对 `try` / `catch`、空结果、超时、解析失败、缓存失效和提前返回，按实际处理分支补齐相应等级的日志；日志与最终返回值、state 或 effect 必须一致。
