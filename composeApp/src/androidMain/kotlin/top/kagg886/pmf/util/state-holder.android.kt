package top.kagg886.pmf.util

import android.os.Parcel
import android.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.kagg886.pmf.PMFApplication

internal actual fun encodePlatformSaveableValue(value: Any?): JsonObject? {
    val parcel = Parcel.obtain()
    return try {
        parcel.writeValue(value)
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("platform"),
                "className" to JsonPrimitive(value?.javaClass?.name.orEmpty()),
                "value" to JsonPrimitive(Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)),
            ),
        )
    } catch (_: Throwable) {
        null
    } finally {
        parcel.recycle()
    }
}

@Suppress("DEPRECATION")
internal actual fun decodePlatformSaveableValue(value: JsonObject): Any? {
    val bytes = Base64.decode(value["value"]?.jsonPrimitive?.contentOrNull ?: return null, Base64.NO_WRAP)
    val parcel = Parcel.obtain()
    return try {
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        parcel.readValue(PMFApplication::class.java.classLoader)
    } catch (_: Throwable) {
        null
    } finally {
        parcel.recycle()
    }
}
