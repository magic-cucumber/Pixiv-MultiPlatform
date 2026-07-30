package top.kagg886.pmf.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.NativeSQLiteDriver
import top.kagg886.pmf.util.databasePath

actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(databasePath.toString())
        .setDriver(NativeSQLiteDriver())
