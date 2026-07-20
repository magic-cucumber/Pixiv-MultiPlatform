package top.kagg886.pmf.backend.database.converters

import androidx.room3.ColumnTypeConverter
import top.kagg886.pmf.backend.database.dao.WatchLaterType

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/11/22 21:18
 * ================================================
 */
class WatchLaterTypeConverter {
    @ColumnTypeConverter
    fun toType(value: String): WatchLaterType = WatchLaterType.valueOf(value)

    @ColumnTypeConverter
    fun fromType(type: WatchLaterType): String = type.name
}
