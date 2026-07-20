package top.kagg886.pmf.backend.database.converters

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 23:22
 * ================================================
 */

class JsonObjectConverter {
    @ColumnTypeConverter
    fun stringToJsonObject(value: String): JsonObject = Json.decodeFromString(value)

    @ColumnTypeConverter
    fun jsonObjectToString(value: JsonObject): String = Json.encodeToString(value)
}
