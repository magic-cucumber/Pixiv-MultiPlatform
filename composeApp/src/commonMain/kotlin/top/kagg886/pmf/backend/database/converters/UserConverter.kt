package top.kagg886.pmf.backend.database.converters

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json
import top.kagg886.pixko.User

class UserConverter {
    @ColumnTypeConverter
    fun stringToUser(value: String): User = Json.decodeFromString<User>(value)

    @ColumnTypeConverter
    fun userToString(value: User): String = Json.encodeToString(value)
}
