package top.kagg886.pmf.backend.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun databaseBuilder() = Room.databaseBuilder<AppDatabase>(name = databasePath).setDriver(BundledSQLiteDriver())
