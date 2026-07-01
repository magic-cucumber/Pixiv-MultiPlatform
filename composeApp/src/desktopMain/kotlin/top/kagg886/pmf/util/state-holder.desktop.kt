package top.kagg886.pmf.util

import kotlinx.serialization.json.JsonObject

internal actual fun encodePlatformSaveableValue(value: Any?): JsonObject? = null

internal actual fun decodePlatformSaveableValue(value: JsonObject): Any? = null
