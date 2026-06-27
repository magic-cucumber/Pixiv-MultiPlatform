package top.kagg886.pmf.backend.database.dao

import androidx.paging.PagingSource
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pmf.backend.database.converters.IllustConverter

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface IllustHistoryDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: IllustHistory)

    @Query("SELECT * FROM IllustHistory ORDER BY createTime DESC")
    fun source(): PagingSource<Int, IllustHistory>
}

@Entity
@ColumnTypeConverters(IllustConverter::class)
data class IllustHistory(
    @PrimaryKey(autoGenerate = false)
    val id: Int,
    val illust: Illust,
    val createTime: Long,
)
