package top.kagg886.pmf.database.common.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
    val timestamp: Long,
    val stacktrace: String? = null,
)
