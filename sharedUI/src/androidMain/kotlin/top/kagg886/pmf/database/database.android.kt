package top.kagg886.pmf.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import top.kagg886.pmf.util.currentApplication
import top.kagg886.pmf.util.databasePath

actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> = Room.databaseBuilder(
    context = currentApplication(),
    name = databasePath.toString(),
)
