package top.kagg886.pmf.database.util

import androidx.room3.RoomDatabase
import androidx.room3.TransactionScope
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/30 15:14
 * ================================================
 */

data class RoomDataBaseLock(private val database: RoomDatabase) {
    suspend fun <R> withWriteLock(block: suspend TransactionScope<R>.() -> R): R = database.withWriteTransaction(block)
    suspend fun <R> withReadLock(block: suspend TransactionScope<R>.() -> R): R = database.withReadTransaction(block)
}
