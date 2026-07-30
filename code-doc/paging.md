# AndroidX Paging 功能笔记

## 定位

Paging 是 AndroidX 提供的分页数据加载库，用来从本地数据库、网络接口或二者合并的大数据集中按页加载数据，并以响应式流的形式交给 UI 渲染。它的目标是减少一次性加载带来的内存、网络和数据库压力，同时内置请求去重、内存缓存、加载状态、错误恢复和 Compose 支持。

官方文档来源：

- 版本说明：https://developer.android.com/jetpack/androidx/releases/paging?hl=zh-cn
- 架构概览：https://developer.android.com/topic/libraries/architecture/paging/v3-overview?hl=zh-cn

截至官方版本说明最后更新的 2026-05-06，`androidx.paging` 当前稳定版是 `3.5.0`；架构概览页最后更新于 2026-06-06，但示例依赖仍写着 `3.4.2`。版本信息以版本说明页的稳定版 `3.5.0` 为准。

## 导入

Gradle version catalog：

```toml
[versions]
paging = "3.5.0"

[libraries]
paging-common = { module = "androidx.paging:paging-common", version.ref = "paging" }
paging-compose = { module = "androidx.paging:paging-compose", version.ref = "paging" }
paging-testing = { module = "androidx.paging:paging-testing", version.ref = "paging" }
```

Compose Multiplatform：

```kotlin
implementation(libs.paging.common)
implementation(libs.paging.compose)
```

测试：

```kotlin
implementation(libs.paging.testing)
```

## 三层架构

Paging 官方推荐的架构分为三层：

```text
Data source
  -> PagingSource
  -> RemoteMediator
  -> Pager + PagingConfig
  -> Flow<PagingData<T>>
  -> Compose LazyPagingItems
```

### 代码库层

`PagingSource<Key, Value>` 定义单一数据源如何加载数据。数据源可以是网络接口、本地数据库、内存数据或其他可分页来源。

`RemoteMediator<Key, Value>` 用于分层数据源，例如：

```text
Remote data source
  -> RemoteMediator
  -> local cache
  -> PagingSource
  -> PagingData
```

这种模式适合离线优先、列表缓存、刷新同步和网络分页落库。

### Pager

`Pager` 用 `PagingConfig` 和 `pagingSourceFactory` 生成 `Flow<PagingData<Value>>`。

```kotlin
val pagingFlow: Flow<PagingData<Item>> =
    Pager(
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 5,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { itemPagingSource }
    ).flow
```

`cachedIn(scope)` 会在指定 `CoroutineScope` 内共享和缓存分页数据，避免多次收集时重复加载。

```kotlin
val items: Flow<PagingData<Item>> =
    pagingFlow.cachedIn(scope)
```

### UI 层

Compose 使用 `paging-compose` 的 `collectAsLazyPagingItems()`：

```kotlin
@Composable
fun ItemList(items: Flow<PagingData<Item>>) {
    val lazyItems = items.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = lazyItems.itemCount,
            key = lazyItems.itemKey { it.id },
            contentType = lazyItems.itemContentType { "item" },
        ) { index ->
            val item = lazyItems[index]
            if (item == null) {
                ItemPlaceholder()
            } else {
                ItemRow(item)
            }
        }
    }
}
```

`LazyPagingItems` 会把访问 item 的位置反馈给 Paging，从而触发预取和追加加载。使用 Compose 时优先通过标准 `items(count, key, contentType)` API 配合 `itemKey` / `itemContentType`，不要再使用已废弃的旧 `items(lazyPagingItems)` / `itemsIndexed(lazyPagingItems)` 风格。

## 核心能力

### 分页加载

`PagingSource.load()` 根据 `LoadType` 和 key 返回 `LoadResult.Page`、`LoadResult.Error` 或 `LoadResult.Invalid`。

常见 key 设计：

- 页码 key：`Int`，适合 `page=1&pageSize=20` 这类接口。
- 游标 key：`String` / token，适合 `nextCursor` / `prevCursor` 接口。
- 条目 key：使用最后一条数据的 id、时间戳或排序字段。
- 数据库偏移：配合 SQL `LIMIT/OFFSET`，但大数据表更推荐稳定排序字段。

### 配置

`PagingConfig` 控制分页行为：

- `pageSize`：每页请求数量。
- `prefetchDistance`：距离列表边缘多少项时预加载。
- `initialLoadSize`：初次加载数量，默认通常是 `pageSize * 3`。
- `enablePlaceholders`：是否为未加载项保留占位。
- `maxSize`：内存中最多保留多少项，超过后可丢弃页面。
- `jumpThreshold`：支持大跨度跳转时的阈值。

### 内存缓存与请求去重

Paging 内置内存中缓存和请求去重。相同 `PagingData` 被多个收集者消费时，可以使用 `cachedIn(scope)` 在指定作用域内共享分页数据，避免重复请求相同数据。

### 占位符

启用 `enablePlaceholders` 后，列表可以知道总数并为未加载项保留位置。UI 需要处理 `null` item。Paging 3.4 起 `PagingData.from` 也支持传入 `placeholdersBefore` 和 `placeholdersAfter`，但滚动这些占位符本身不会触发加载。

### 加载状态

Paging 暴露 `LoadState` 和 `CombinedLoadStates`，区分：

- `refresh`：初次加载或刷新。
- `append`：向列表尾部加载。
- `prepend`：向列表头部加载。
- `Loading`：加载中。
- `NotLoading`：当前方向没有加载。
- `Error`：加载失败，可显示错误 UI 并允许重试。

Compose 中常用：

```kotlin
when (val state = lazyItems.loadState.refresh) {
    is LoadState.Loading -> FullScreenLoading()
    is LoadState.Error -> ErrorView(
        message = state.error.message.orEmpty(),
        onRetry = lazyItems::retry,
    )
    is LoadState.NotLoading -> Unit
}
```

`append` / `prepend` 可用于底部或顶部的局部 loading、错误和 retry：

```kotlin
item {
    when (lazyItems.loadState.append) {
        is LoadState.Loading -> FooterLoading()
        is LoadState.Error -> FooterRetry(onRetry = lazyItems::retry)
        is LoadState.NotLoading -> Unit
    }
}
```

Paging 3.3 增加了 `LoadStates` / `CombinedLoadStates` 的 `hasError`、`isIdle` 辅助能力，以及 `Flow<CombinedLoadStates>.awaitNotLoading()`。

### 刷新与重试

UI 可以调用：

- `LazyPagingItems.refresh()`：丢弃旧代数据并重新加载。
- `LazyPagingItems.retry()`：重试失败的加载。

Paging 3.5 还为使用快照状态流的场景加入了 `Pager.refresh` 和 `Pager.retry`。

### 转换

`PagingData` 支持常见数据变换：

```kotlin
pagingFlow
    .map { pagingData ->
        pagingData.map { item -> item.toUiModel() }
    }
```

常见用途：

- DTO / entity 转 domain / UI model。
- 插入 header、separator 或 footer。
- 过滤不该展示的数据。
- 附加本地 UI 状态，例如选中、收藏、下载进度。

### Compose 延迟布局

Paging Compose 支持 `LazyColumn`、`LazyRow`、`LazyVerticalGrid`、`HorizontalPager`，也支持 Wear 和 TV 中使用标准 lazy item API 的自定义延迟组件。关键是使用 `LazyPagingItems.itemKey` 和 `LazyPagingItems.itemContentType` 为标准 `items` API 提供稳定 key 和内容类型。

## Paging 3.5 重点

Paging 3.5.0 于 2026-05-06 发布。

主要新增能力来自 3.5.0 alpha / beta：

- `paging-common` 增加 `Flow<PagingData>.asItemSnapshotListFlow`，用于把分页数据流转换为 `Flow<ItemSnapshotList>`。
- 这个快照流可以与其他数据源合并、被多个收集者共享、作为 UI state 暴露、进行本地缓存或内存中修改。
- `Pager.append` 和 `Pager.prepend` 支持手动触发两端加载，适合不完全依赖滚动触发的界面。
- `Pager.refresh` 和 `Pager.retry` 支持在使用快照状态流时刷新和错误恢复。

注意：早期 alpha 名称是 `asState`，3.5.0 beta01 已重命名为 `asItemSnapshotListFlow`，文档或示例中看到 `asState` 时应按新名称迁移。

## 近期版本补充

### Paging 3.4

Paging 3.4 的稳定版是 `3.4.2`，主要补充：

- `paging-common`、`paging-testing`、`paging-compose` 扩展 KMP 目标，覆盖 JVM、Native 和 Web。
- `PagingState.closestItemAroundPosition` 可查找最接近目标位置且满足谓词的已加载项，适合生成更稳定的 item-based refresh key。
- `PagingData.from` 支持 `placeholdersBefore` 和 `placeholdersAfter`。

### Paging 3.3

Paging 3.3 主要补充：

- `paging-common`、`paging-testing` 开始提供 KMP 兼容制品，`paging-compose` 也跟随 Compose 多平台支持。
- 新增 `PagingSourceFactory` 功能接口，比 `() -> PagingSource` lambda 表达更明确。
- `paging-testing` 增加 `List<Value>.asPagingSourceFactory()`，方便用不可变列表构造测试数据源。
- `LoadStates` / `CombinedLoadStates` 增加 `hasError`、`isIdle`，并提供 `awaitNotLoading()`。

## Kotlin Multiplatform 支持

从 Paging 3.3 开始，Paging 提供 KMP 兼容制品；3.4 继续扩展目标平台。

当前需要区分：

- `paging-common`：Paging 3 核心 API，支持 KMP。
- `paging-testing`：测试 API，支持 KMP。
- `paging-compose`：Compose 支持，支持 KMP，并匹配 Compose Multiplatform。

Paging 3.4 后 `paging-common`、`paging-testing`、`paging-compose` 支持 JVM（Android 和桌面）、Native（Linux、iOS、watchOS、tvOS、macOS、MinGW）和 Web（JavaScript、WasmJS）。同时移除了 `macosX64`、`iosX64`、`watchosX64` 和 `tvosX64` 目标，以跟随 JetBrains 对相关目标的弃用。

## 测试

`paging-testing` 提供专门测试 Paging 行为的 API。

常用能力：

- `Flow<PagingData<T>>.asSnapshot { ... }`：把分页流收集为普通 `List<T>`，并在 lambda 中模拟滚动。
- `scrollTo(index)`：模拟滚动到指定位置。
- `appendScrollWhile { ... }`：持续向后滚动直到条件不满足。
- `asPagingSourceFactory()`：从 `Flow<List<T>>` 或不可变 `List<T>` 构建可复用的 `PagingSourceFactory`。
- `TestPager`：测试 `PagingSource` 行为。

示例：

```kotlin
@Test
fun pagingDataContainsExpectedItem() = runTest {
    val snapshot = pagingFlow.asSnapshot {
        scrollTo(index = 50)
    }

    assertTrue(snapshot.any { it.id == expectedId })
}
```

Paging 3.2 后 `asSnapshot` 默认 `loadOperations` 为空 lambda，可直接获取初始刷新结果；后续版本也降低了对 main dispatcher 的要求。
