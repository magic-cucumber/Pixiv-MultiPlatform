package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import top.kagg886.pmf.database.account.entity.PageKey

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 14:01
 * ================================================
 */

@Dao
interface PageKeyDao {
    @Insert
    suspend fun insert(record: PageKey)

    @Query("SELECT * FROM page_key WHERE tag = :tag ORDER BY page DESC LIMIT 1")
    suspend fun last(tag: String): PageKey?

    @Query("DELETE FROM page_key WHERE tag = :tag")
    suspend fun clean(tag: String)
}
