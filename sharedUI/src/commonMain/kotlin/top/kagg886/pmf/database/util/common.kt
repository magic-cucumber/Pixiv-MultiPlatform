package top.kagg886.pmf.database.util

import androidx.room3.RoomDatabase

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/31 15:16
 * ================================================
 */

fun <T : RoomDatabase> RoomDatabase.Builder<T>.commonBuilder() =
    fallbackToDestructiveMigration()
        .fallbackToDestructiveMigrationFrom(true, 1)
        .fallbackToDestructiveMigrationOnDowngrade()
