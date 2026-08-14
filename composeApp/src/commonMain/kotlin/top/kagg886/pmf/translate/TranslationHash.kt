package top.kagg886.pmf.translate

import okio.ByteString.Companion.encodeUtf8

/** 生成稳定的原文/配置指纹，供翻译缓存作为 key 使用。 */
fun translationHash(text: String): String = text.trim().encodeUtf8().sha256().hex()
