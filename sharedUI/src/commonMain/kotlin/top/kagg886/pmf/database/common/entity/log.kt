package top.kagg886.pmf.database.common.entity

import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

@Entity(
    tableName = "log",
    indices = [Index(value = ["timestamp"]), Index(value = ["severity", "timestamp"])]
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tag: String,
    val severity: Int,
    val message: String,
    @ColumnTypeConverters(InstantConverter::class)
    val timestamp: Instant,
    val stacktrace: String? = null,
) {
    class InstantConverter {
        @ColumnTypeConverter
        fun fromEpochMilliseconds(value: Long): Instant =
            Instant.fromEpochMilliseconds(value)

        @ColumnTypeConverter
        fun toEpochMilliseconds(value: Instant): Long =
            value.toEpochMilliseconds()
    }
}
