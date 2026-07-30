# 缓存 ID

`IllustCache.id` 和 `UserCache.id` 将登录用户的 Pixiv 用户 ID 与对应的
Pixiv 资源 ID 组合起来。结果是一个可逆的缓存 ID，因此数据库无需为这些表及其
Illust 关联表单独设置登录用户列。

两个源 ID 均为非负 `Int` 值，因此每个 ID 都可容纳在 31 位中。
这对 ID 被打包为一个非负 `Long`：

```kotlin
private const val RawIdMask = 0x7FFF_FFFFL

fun cacheIdOf(loginUserId: Int, pixivId: Int): Long {
    require(loginUserId >= 0) { "loginUserId must be non-negative" }
    require(pixivId >= 0) { "pixivId must be non-negative" }
    return (loginUserId.toLong() shl 31) or pixivId.toLong()
}

fun loginUserIdOf(cacheId: Long): Int = (cacheId ushr 31).toInt()

fun pixivIdOf(cacheId: Long): Int = (cacheId and RawIdMask).toInt()
```

打包后的值位于 `[0, 2^62 - 1]`，可容纳在 SQLite 的有符号 `INTEGER`
以及 Kotlin 的 `Long` 中。该映射在两个非负 31 位整数上是双射：可以从缓存 ID
恢复任一原始 ID，且给定的一对 ID 始终只会生成一个缓存 ID。

若要查询某个登录用户拥有的全部 Illust 行，请使用下面的主键范围。由于 `id` 是
表的主键，该范围对索引友好。

```kotlin
fun cacheIdRangeOf(loginUserId: Int): LongRange {
    require(loginUserId >= 0)
    val start = loginUserId.toLong() shl 31
    return start..(start + RawIdMask)
}
```

`TagCache.id` 和 `ImageUrlsCache.id` 是由仓库生成的 UUID 字符串。它们没有可供
打包的 Pixiv 数字标识。它们的归属由以打包缓存 ID 为根的 Illust/User 外键图约束。
