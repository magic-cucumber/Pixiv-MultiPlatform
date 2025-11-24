package top.kagg886.pmf.backend.database.converters

import androidx.room.TypeConverter
import top.kagg886.pmf.backend.database.dao.WatchLaterType

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 21:18
 * ================================================
 */
class WatchLaterTypeConverter {
    @TypeConverter
    fun toType(value: String): WatchLaterType = WatchLaterType.valueOf(value)

    @TypeConverter
    fun fromType(type: WatchLaterType): String = type.name
}
