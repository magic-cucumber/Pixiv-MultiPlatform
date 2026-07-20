package top.kagg886.pmf.backend.database.converters

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json
import top.kagg886.pixko.module.novel.Novel

class NovelConverter {
    @ColumnTypeConverter
    fun stringToNovel(value: String): Novel = Json.decodeFromString<Novel>(value)

    @ColumnTypeConverter
    fun novelToString(value: Novel): String = Json.encodeToString(value)
}
