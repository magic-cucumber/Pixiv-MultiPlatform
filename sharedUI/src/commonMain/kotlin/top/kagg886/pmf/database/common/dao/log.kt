package top.kagg886.pmf.database.common.dao

import androidx.paging.PagingSource
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import top.kagg886.pmf.database.common.entity.LogEntity
import top.kagg886.pmf.database.common.entity.LogEntity.InstantConverter
import kotlin.time.Instant

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM log")
    suspend fun clear()


    @Query("DELETE FROM log where timestamp < :deadline")
    @ColumnTypeConverters(InstantConverter::class)
    suspend fun clearBeforeTime(deadline: Instant)

    @Query("SELECT * FROM log WHERE (:severity IS NULL OR severity = :severity) ORDER BY timestamp DESC")
    fun getAll(severity: Int? = null): PagingSource<Int, LogEntity>
}
