package top.kagg886.pmf.database.account.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import top.kagg886.pmf.database.account.entity.UserCache

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(item: UserCache)

    @Query("SELECT * FROM user_cache WHERE userId = :userId")
    suspend fun find(userId: Long): UserCache?

    @Query("DELETE FROM user_cache WHERE userId = :userId")
    suspend fun delete(userId: Long)
}
