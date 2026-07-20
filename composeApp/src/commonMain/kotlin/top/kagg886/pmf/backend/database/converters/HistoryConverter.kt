package top.kagg886.pmf.backend.database.converters

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json

class HistoryConverter {
    @ColumnTypeConverter
    fun stringToListString(value: String): List<String> = Json.decodeFromString(value)

    @ColumnTypeConverter
    fun listStringToString(value: List<String>): String = Json.encodeToString(value)
}
