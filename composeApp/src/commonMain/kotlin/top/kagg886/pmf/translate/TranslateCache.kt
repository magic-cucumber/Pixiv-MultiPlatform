package top.kagg886.pmf.translate

/**
 * 持久化翻译缓存接口。
 *
 * [key] 为原文哈希，[fingerprint] 为翻译配置指纹；两者共同决定一条缓存是否可用。
 */
interface TranslateCache {
    suspend fun get(key: String, fingerprint: String): String?

    suspend fun put(key: String, fingerprint: String, value: String)
}
