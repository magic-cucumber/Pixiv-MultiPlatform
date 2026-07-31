package top.kagg886.pmf.database.common.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import top.kagg886.pmf.database.common.entity.LogEntity

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM log")
    suspend fun clear()

    @Query("SELECT * FROM log ORDER BY timestamp DESC")
    suspend fun getAll(): List<LogEntity>
}
