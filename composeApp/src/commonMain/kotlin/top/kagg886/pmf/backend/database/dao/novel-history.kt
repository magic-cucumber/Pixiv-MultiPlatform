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
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pmf.backend.database.converters.NovelConverter

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface NovelHistoryDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NovelHistory)

    @Query("SELECT * FROM NovelHistory ORDER BY createTime DESC")
    fun source(): PagingSource<Int, NovelHistory>
}

@Entity
@ColumnTypeConverters(NovelConverter::class)
data class NovelHistory(
    @PrimaryKey(autoGenerate = false)
    val id: Long,
    val novel: Novel,
    val createTime: Long,
)
