package top.kagg886.pmf.backend.database.converters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import top.kagg886.pixko.User

class UserConverter {
    @TypeConverter
    fun stringToUser(value: String): User = Json.decodeFromString<User>(value)

    @TypeConverter
    fun userToString(value: User): String = Json.encodeToString(value)
}
