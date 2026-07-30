package top.kagg886.pmf.database.dao

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

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

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM log")
    suspend fun clear()

    @Query("SELECT * FROM log ORDER BY timestamp DESC")
    suspend fun getAll(): List<LogEntity>
}
