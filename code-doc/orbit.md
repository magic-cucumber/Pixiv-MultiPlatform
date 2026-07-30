# Orbit MVI 接入笔记

## 定位

Orbit 是 Kotlin Multiplatform 的 MVI 框架，用来统一管理页面状态、用户事件、业务逻辑和一次性副作用。它提供 `Container`、`ContainerHost`、`intent`、`reduce`、`postSideEffect` 等 API，让状态更新保持单向、集中和可测试。

在这个项目中，推荐放置层次如下：

```text
Compose UI
  -> Orbit ViewModel / ContainerHost
  -> Container
  -> State / SideEffect
```

Composable 只负责展示状态和转发事件；ViewModel 持有 Orbit `Container`，在 `intent { ... }` 中执行业务逻辑，并通过 `reduce { ... }` 更新状态，通过 `postSideEffect(...)` 发送导航、Toast、Snackbar 等一次性事件。

## 依赖

在版本目录中增加 Orbit：

```toml
[versions]
orbit = "10.0.0"

[libraries]
orbit-core = { module = "org.orbit-mvi:orbit-core", version.ref = "orbit" }
orbit-viewmodel = { module = "org.orbit-mvi:orbit-viewmodel", version.ref = "orbit" }
orbit-compose = { module = "org.orbit-mvi:orbit-compose", version.ref = "orbit" }
orbit-test = { module = "org.orbit-mvi:orbit-test", version.ref = "orbit" }
```

然后在 `sharedUI/build.gradle.kts` 的 `commonMain.dependencies` 中加入：

```kotlin
implementation(libs.orbit.core)
implementation(libs.orbit.viewmodel)
implementation(libs.orbit.compose)
```

在 `commonTest.dependencies` 中按需加入：

```kotlin
implementation(libs.orbit.test)
```

当前项目已经在 `sharedUI` 中使用 Compose Multiplatform 和 `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`，因此 Orbit 的 ViewModel 和 Compose 集成也适合放在 `sharedUI`。

## 基本模型

建议把页面状态、一次性副作用和用户事件分开：

```text
State       可重复渲染的 UI 状态
SideEffect 只消费一次的 UI 事件，例如导航、Toast、Snackbar
Intent      ViewModel 暴露给 UI 调用的方法，不一定需要单独建类
```

示例：

```kotlin
data class ExampleState(
    val isLoading: Boolean = false,
    val title: String = "",
    val errorMessage: String? = null,
)

sealed interface ExampleSideEffect {
    data class Toast(val message: String) : ExampleSideEffect
    data object Back : ExampleSideEffect
}
```

状态和副作用建议使用不可变类型，例如 `data class`、`sealed interface`、`data object`。

## ViewModel

ViewModel 实现 `ContainerHost<State, SideEffect>`，并通过 `container(...)` 创建 Orbit 容器：

```kotlin
import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.postSideEffect
import org.orbitmvi.orbit.syntax.simple.reduce
import org.orbitmvi.orbit.viewmodel.container

class ExampleViewModel : ViewModel(), ContainerHost<ExampleState, ExampleSideEffect> {

    override val container = container<ExampleState, ExampleSideEffect>(
        initialState = ExampleState(),
    )

    fun load() = intent {
        reduce {
            state.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        runCatching {
            loadTitle()
        }.onSuccess { title ->
            reduce {
                state.copy(
                    isLoading = false,
                    title = title,
                )
            }
        }.onFailure { error ->
            reduce {
                state.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "加载失败",
                )
            }
            postSideEffect(ExampleSideEffect.Toast("加载失败"))
        }
    }

    fun retry() = intent {
        load()
    }

    fun onBackClick() = intent {
        postSideEffect(ExampleSideEffect.Back)
    }

    private suspend fun loadTitle(): String {
        return "Orbit"
    }
}
```

`intent { ... }` 是业务逻辑入口；`state` 表示当前容器状态；`reduce { ... }` 原子更新状态；`postSideEffect(...)` 发送一次性事件。长耗时工作可以在 `intent` 中切换协程上下文，避免阻塞 Orbit 的事件循环。

## 初始化和 Flow 收集

如果需要在容器创建时收集长期 `Flow`，可以使用 `repeatOnSubscription { ... }` 控制订阅生命周期：

```kotlin
import kotlinx.coroutines.flow.collectLatest
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.reduce
import org.orbitmvi.orbit.syntax.simple.repeatOnSubscription
import org.orbitmvi.orbit.viewmodel.container

class ExampleViewModel(
    private val ticker: Flow<String>,
) : ViewModel(), ContainerHost<ExampleState, ExampleSideEffect> {

    override val container = container<ExampleState, ExampleSideEffect>(
        initialState = ExampleState(),
    ) {
        repeatOnSubscription {
            ticker.collectLatest { title ->
                reduce {
                    state.copy(
                        isLoading = false,
                        title = title,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    fun refresh() = intent {
        reduce { state.copy(isLoading = true) }
    }
}
```

`repeatOnSubscription` 会在状态或副作用有观察者时启动内部收集，在没有观察者时停止。对于只执行一次的按钮事件，继续用普通 `intent` 方法即可。

## SavedState

Orbit `orbit-viewmodel` 支持把容器状态接入 `SavedStateHandle`。如果是 multiplatform 状态保存，状态需要支持 Kotlinx Serialization：

```kotlin
import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.viewmodel.container

@Serializable
data class ExampleState(
    val selectedId: String = "",
    val isLoading: Boolean = false,
)

class ExampleViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ContainerHost<ExampleState, Nothing> {

    override val container = container<ExampleState, Nothing>(
        initialState = ExampleState(),
        savedStateHandle = savedStateHandle,
        serializer = ExampleState.serializer(),
    )
}
```

`SavedStateHandle` 只适合保存小型、临时的 UI 状态，例如 id、tab、筛选条件。大型列表、缓存数据和关键业务数据不适合放在 `SavedStateHandle`。

## Compose 使用方式

Compose 侧通过 `orbit-compose` 订阅 ViewModel：

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun ExampleRoute(
    viewModel: ExampleViewModel,
    onBack: () -> Unit,
    showToast: (String) -> Unit,
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            ExampleSideEffect.Back -> onBack()
            is ExampleSideEffect.Toast -> showToast(sideEffect.message)
        }
    }

    ExampleScreen(
        state = state,
        onRefresh = viewModel::retry,
        onBack = viewModel::onBackClick,
    )
}
```

页面内容组件只接收状态和回调，方便预览和 UI 测试：

```kotlin
@Composable
fun ExampleScreen(
    state: ExampleState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    when {
        state.isLoading -> {
            CircularProgressIndicator()
        }

        state.title.isNotBlank() -> {
            Column {
                Text(state.title)
                Button(onClick = onRefresh) {
                    Text("刷新")
                }
            }
        }

        state.errorMessage != null -> {
            Column {
                Text(state.errorMessage)
                Button(onClick = onRefresh) {
                    Text("重试")
                }
            }
        }
    }
}
```

不要在 `ExampleScreen` 里直接访问 ViewModel。把 ViewModel 访问限制在 `ExampleRoute` 这一层，可以让 UI 组件保持可测试、可预览。

## TextField 状态

普通输入框可以继续把 `String` 放在 `State` 中，再通过 ViewModel 方法更新：

```kotlin
data class SearchState(
    val keyword: String = "",
    val isValid: Boolean = true,
)

fun onKeywordChange(keyword: String) = intent {
    reduce {
        state.copy(
            keyword = keyword,
            isValid = keyword.length <= 20,
        )
    }
}
```

如果需要把 Compose `TextFieldState` 交给 ViewModel 持有，官方建议使用 `snapshotFlow { textFieldState.text }` 观察输入变化，再在 Orbit 中更新校验状态。这个方式适合复杂输入、自动补全和需要避免输入丢失的场景；简单表单不必一开始就引入。

## 推荐接入顺序

1. 在版本目录和 `sharedUI/build.gradle.kts` 中加入 `orbit-core`、`orbit-viewmodel`、`orbit-compose`。
2. 为目标页面定义 `State` 和 `SideEffect`。
3. 创建实现 `ContainerHost<State, SideEffect>` 的 ViewModel。
4. 在 ViewModel 的 `intent` 中执行业务逻辑。
5. 用 `reduce` 把业务结果转换成页面状态。
6. 用 `postSideEffect` 处理导航、Toast、Snackbar 等一次性事件。
7. 在 Compose Route 层使用 `collectAsState()` 和 `collectSideEffect { ... }`。
8. 把纯 UI 内容拆成只接收 `state` 和回调的 Composable。
9. 需要长期订阅 Flow 时，再引入 `repeatOnSubscription`。
10. 需要状态恢复时，再接入 `SavedStateHandle` 和 Kotlinx Serialization。

## 注意事项

- Orbit 放在 UI 状态层，不要把框架对象暴露到纯 UI 内容组件中。
- `State` 应该是稳定、可比较、不可变的数据结构。
- `SideEffect` 只放一次性事件，不要把可重复渲染的数据放进去。
- `collectSideEffect` 通常只保留一个观察者，避免一次性事件被多处消费导致行为不可预测。
- `intent` 内部要自己处理异常；默认不要假设 Orbit 会替你把错误转换成 UI 状态。
- 长期收集 `Flow` 时优先使用 `repeatOnSubscription`，避免 UI 不可见时继续做昂贵订阅。
- Compose 中保留 `Route -> Screen` 分层，Route 连接 ViewModel，Screen 只负责渲染。
- `SavedStateHandle` 不适合保存大对象、列表和关键持久化数据。
- 如果项目只需要简单状态，先接 `ViewModel + collectAsState`；副作用、SavedState、测试工具可以按需求逐步加入。

参考文档：

- https://orbit-mvi.org/
- https://orbit-mvi.org/Core/
- https://orbit-mvi.org/ViewModel/
- https://orbit-mvi.org/Compose/
