package top.kagg886.pmf.backend.database.converters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.kagg886.pmf.backend.database.dao.WatchLaterType

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 23:22
 * ================================================
 */


class JsonObjectConverter {
    @TypeConverter
    fun stringToJsonObject(value: String): JsonObject = Json.decodeFromString(value)

    @TypeConverter
    fun jsonObjectToString(value: JsonObject): String = Json.encodeToString(value)
}
