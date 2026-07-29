# 项目开发规范

本文件规定本项目的开发方式，所有参与开发的人员与智能体必须遵守。

## 国际化文本

- 所有用户可见文本都必须使用国际化资源；不要在界面代码中直接写文本。
- 按功能在 `sharedUI/i18n/src/i18n/` 中新增或维护 YAML 文件。每个文本使用稳定且语义明确的标识，并同时提供英文与简体中文。

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
