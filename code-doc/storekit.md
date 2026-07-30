# Store5 功能笔记

## 定位

Store5 是一个用于组织数据读取、缓存、刷新、本地数据源和远端同步的 Kotlin Multiplatform 库。

核心读取流程：

```text
StoreReadRequest
  -> Store
  -> Memory Cache
  -> SourceOfTruth
  -> Fetcher
  -> StoreReadResponse
```

核心写入流程：

```text
StoreWriteRequest
  -> MutableStore
  -> local write
  -> Updater
  -> Bookkeeper
  -> StoreWriteResponse
```

## 依赖

```toml
[versions]
store = "5.0.0"

[libraries]
store = { module = "org.mobilenativefoundation.store:store5", version.ref = "store" }
```

```kotlin
implementation(libs.store)
```

## 三层模型

Store5 通常会涉及三类数据模型：

```text
NetworkItem  远端数据模型
CacheItem    本地缓存模型
DomainItem   对外暴露的领域模型
```

通用示例：

```kotlin
data class NetworkItem(
    val id: String,
    val title: String,
)

data class CacheItem(
    val id: String,
    val title: String,
)

data class DomainItem(
    val id: String,
    val title: String,
)

data class UpdateResponse(
    val accepted: Boolean,
)

fun NetworkItem.toCache(): CacheItem =
    CacheItem(
        id = id,
        title = title,
    )

fun NetworkItem.toDomain(): DomainItem =
    DomainItem(
        id = id,
        title = title,
    )

fun CacheItem.toDomain(): DomainItem =
    DomainItem(
        id = id,
        title = title,
    )

fun DomainItem.toCache(): CacheItem =
    CacheItem(
        id = id,
        title = title,
    )
```

## 核心组件

| 组件 | 功能 |
| --- | --- |
| `Store` | 读取入口，协调内存缓存、Source of Truth、Fetcher 和 Validator |
| `MutableStore` | 在 `Store` 的读能力上增加写入、同步和冲突处理 |
| `Fetcher` | 定义如何从远端读取 `NetworkItem` |
| `SourceOfTruth` | 定义如何读取、写入和删除本地 `CacheItem`，并对外发出 `DomainItem` |
| `Updater` | 定义如何把 `DomainItem` 同步到远端 |
| `Bookkeeper` | 记录同步失败，帮助后续重试或冲突处理 |
| `Validator` | 判断缓存或本地数据是否仍然有效 |
| `Converter` | 在 `NetworkItem`、`CacheItem`、`DomainItem` 之间转换 |

## Store

`Store<Key, DomainItem>` 是读取数据的核心接口。

```kotlin
interface Store<Key : Any, Output : Any> {
    fun stream(request: StoreReadRequest<Key>): Flow<StoreReadResponse<Output>>
    suspend fun clear(key: Key)
    suspend fun clearAll()
}
```

`stream` 返回 `Flow<StoreReadResponse<DomainItem>>`，读取结果可能是 loading、data、error 或 no new data。

`clear(key)` 和 `clearAll()` 清理本地内存缓存和 Source of Truth，不表示远端删除。

## Fetcher

`Fetcher<Key, NetworkItem>` 定义远端读取逻辑。

```kotlin
val fetcher = Fetcher.of<String, NetworkItem> { key ->
    fetchNetworkItem(key)
}
```

Fetcher 可以返回单个结果，也可以通过 `Flow` 返回多个结果。它的结果会被 Store 包装成 `FetcherResult`，用于表示成功数据或错误。

常见错误形态：

```kotlin
FetcherResult.Error.Exception(error)
FetcherResult.Error.Message(message)
FetcherResult.Error.Custom(error)
```

## SourceOfTruth

`SourceOfTruth<Key, CacheItem, DomainItem>` 定义本地权威数据源。

```kotlin
interface SourceOfTruth<Key : Any, Local : Any, Output : Any> {
    fun reader(key: Key): Flow<Output?>
    suspend fun write(key: Key, value: Local)
    suspend fun delete(key: Key)
    suspend fun deleteAll()
}
```

通用示例：

```kotlin
val sourceOfTruth = SourceOfTruth.of<String, NetworkItem, DomainItem>(
    reader = { key ->
        observeCacheItem(key)
            .map { cacheItem ->
                cacheItem?.toDomain()
            }
    },
    writer = { key, networkItem ->
        writeCacheItem(
            key = key,
            value = networkItem.toCache(),
        )
    },
    delete = { key ->
        deleteCacheItem(key)
    },
    deleteAll = {
        deleteAllCacheItems()
    },
)
```

`reader(key)` 应返回可观察的 `Flow`。当本地数据变化时，Store 可以继续向订阅者发出新的 `StoreReadResponse.Data`。

## Converter

`Converter<NetworkItem, CacheItem, DomainItem>` 负责类型转换。

```kotlin
interface Converter<Network : Any, Local : Any, Output : Any> {
    fun fromNetworkToLocal(network: Network): Local
    fun fromOutputToLocal(output: Output): Local
}
```

通用示例：

```kotlin
val converter = Converter.Builder<NetworkItem, CacheItem, DomainItem>()
    .fromNetworkToLocal { networkItem ->
        networkItem.toCache()
    }
    .fromOutputToLocal { domainItem ->
        domainItem.toCache()
    }
    .build()
```

当 Store 从 Fetcher 得到 `NetworkItem` 时，可以通过 Converter 转成 `CacheItem` 后写入 Source of Truth。当 MutableStore 本地写入 `DomainItem` 时，也可以通过 Converter 转成 `CacheItem`。

## Validator

`Validator<DomainItem>` 判断缓存或 Source of Truth 中的数据是否有效。

```kotlin
interface Validator<Output : Any> {
    suspend fun isValid(item: Output): Boolean
}
```

通用示例：

```kotlin
val validator = Validator.by<DomainItem> { item ->
    item.title.isNotBlank()
}
```

如果没有配置 Validator，Store 默认认为缓存数据有效。Validator 应保持轻量，只做有效性判断，不修改数据或外部状态。

## StoreBuilder

最小 Store 可以只配置 Fetcher：

```kotlin
val store: Store<String, DomainItem> =
    StoreBuilder.from(
        fetcher = Fetcher.of { key: String ->
            fetchNetworkItem(key).toDomain()
        },
    ).build()
```

带 Source of Truth 的 Store：

```kotlin
val store: Store<String, DomainItem> =
    StoreBuilder.from(
        fetcher = Fetcher.of { key: String ->
            fetchNetworkItem(key)
        },
        sourceOfTruth = sourceOfTruth,
    ).validator(
        validator = validator,
    ).build()
```

实际构建 API 可能随 Store5 版本变化。概念上，`StoreBuilder` 用于组合 Fetcher、Source of Truth、Validator、Converter、内存缓存等能力。

## 读请求

常见读请求：

```kotlin
store.stream(
    StoreReadRequest.cached(
        key = "item-id",
        refresh = false,
    ),
)

store.stream(
    StoreReadRequest.cached(
        key = "item-id",
        refresh = true,
    ),
)

store.stream(
    StoreReadRequest.fresh(
        key = "item-id",
    ),
)
```

含义：

- `cached(..., refresh = false)`：优先读取内存缓存和 Source of Truth，必要时再调用 Fetcher。
- `cached(..., refresh = true)`：允许先返回本地已有数据，同时触发远端刷新。
- `fresh(...)`：跳过缓存检查，直接通过 Fetcher 获取新数据。

## 读链路

典型读取流程：

```text
1. 调用 Store.stream(request)
2. 检查 Memory Cache
3. 用 Validator 判断缓存是否有效
4. 读取 SourceOfTruth.reader(key)
5. 用 Validator 判断本地数据是否有效
6. 必要时调用 Fetcher(key)
7. 将 Fetcher 结果写入 Memory Cache 和 SourceOfTruth
8. 通过 StoreReadResponse 发出结果
```

如果 Source of Truth 存在，Store 会通过同步屏障协调读写，避免读取和写入并发导致的数据不一致。

## MutableStore

`MutableStore<Key, DomainItem>` 在读取能力之外增加写入能力。

```kotlin
interface MutableStore<Key : Any, Output : Any> {
    fun stream(
        request: StoreReadRequest<Key>,
    ): Flow<StoreReadResponse<Output>>

    suspend fun <Response : Any> write(
        request: StoreWriteRequest<Key, Output, Response>,
    ): StoreWriteResponse

    suspend fun clear(key: Key)
    suspend fun clearAll()
}
```

它的读取流程委托给内部 `Store`，写入流程由本地写入、远端同步和失败记录组成。

## Updater

`Updater<Key, DomainItem, Response>` 定义远端写入逻辑。

```kotlin
interface Updater<Key : Any, Output : Any, Response : Any> {
    suspend fun post(key: Key, value: Output): UpdaterResult
    val onCompletion: OnUpdaterCompletion<Response>?
}
```

通用示例：

```kotlin
val updater = Updater.by<String, DomainItem, UpdateResponse>(
    post = { key, value ->
        updateNetworkItem(key, value)
    },
)
```

`post` 成功后，MutableStore 可以移除已处理的写请求，并清理 Bookkeeper 中对应 key 的失败记录。失败时，写请求会保留以便后续重试。

## Bookkeeper

`Bookkeeper<Key>` 记录同步失败。

```kotlin
interface Bookkeeper<Key : Any> {
    suspend fun getLastFailedSync(key: Key): Long?
    suspend fun setLastFailedSync(key: Key, timestamp: Long = now()): Boolean
    suspend fun clear(key: Key): Boolean
    suspend fun clearAll(): Boolean
}
```

通用示例：

```kotlin
val bookkeeper = Bookkeeper.by<String>(
    getLastFailedSync = { key ->
        readLastFailedSync(key)
    },
    setLastFailedSync = { key, timestamp ->
        writeLastFailedSync(key, timestamp)
    },
    clear = { key ->
        clearLastFailedSync(key)
    },
    clearAll = {
        clearAllFailedSyncs()
    },
)
```

Bookkeeper 常用于记录某个 key 的最后一次失败同步时间。MutableStore 可以据此在后续读写时处理未同步变更。

## 写请求

通用写入示例：

```kotlin
val response = mutableStore.write<UpdateResponse>(
    StoreWriteRequest.of(
        key = "item-id",
        value = DomainItem(
            id = "item-id",
            title = "Updated",
        ),
    ),
)
```

典型写入流程：

```text
1. 调用 MutableStore.write(request)
2. 写请求进入 per-key queue
3. 先写入本地 Store / SourceOfTruth
4. 调用 Updater.post(key, value)
5. 成功时移除队列中的已处理请求，并清理 Bookkeeper
6. 失败时记录 Bookkeeper，并保留请求等待后续处理
7. 返回 StoreWriteResponse
```

## 能力组合

| 需要的能力 | 组件组合 |
| --- | --- |
| 远端读取 | `Fetcher + Store` |
| 本地权威数据源 | `Fetcher + SourceOfTruth + Store` |
| 缓存有效性判断 | `Validator` |
| 三层模型转换 | `Converter` |
| 本地写入和远端同步 | `MutableStore + Updater` |
| 失败同步记录 | `Bookkeeper` |

## 注意事项

- `Key` 应稳定且能唯一表示一个数据项或查询。
- `Store` 主要处理读取；`MutableStore` 处理本地写入和远端同步。
- `clear(key)` 和 `clearAll()` 清理本地数据，不表达远端删除语义。
- `SourceOfTruth.reader(key)` 应优先返回可观察的 `Flow`。
- `Validator` 应只判断数据是否有效，不做副作用操作。
- `Bookkeeper` 的失败记录需要可靠保存，避免丢失未同步状态。
- `Fetcher` 负责远端读取，`Updater` 负责远端写入，两者职责不同。

## 参考文档

- Overview：https://store.mobilenativefoundation.org/docs/concepts/store5/overview
- Store：https://store.mobilenativefoundation.org/docs/concepts/store5/store
- Mutable Store：https://store.mobilenativefoundation.org/docs/concepts/store5/mutable-store
- Source of Truth：https://store.mobilenativefoundation.org/docs/concepts/store5/source-of-truth
- Fetcher：https://store.mobilenativefoundation.org/docs/concepts/store5/fetcher
- Updater：https://store.mobilenativefoundation.org/docs/concepts/store5/updater
- Bookkeeper：https://store.mobilenativefoundation.org/docs/concepts/store5/bookkeeper
- Validator：https://store.mobilenativefoundation.org/docs/concepts/store5/validator
- Converter：https://store.mobilenativefoundation.org/docs/concepts/store5/converter
- Quickstart：https://store.mobilenativefoundation.org/docs/quickstart
