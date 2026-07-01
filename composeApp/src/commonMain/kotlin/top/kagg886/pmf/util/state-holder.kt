@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.util

import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistryWrapper
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import co.touchlab.kermit.Logger
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.kagg886.pmf.backend.SystemConfig

private val stateHolderLogger = Logger.withTag("SaveableStateHolder")

internal expect fun encodePlatformSaveableValue(value: Any?): JsonObject?

internal expect fun decodePlatformSaveableValue(value: JsonObject): Any?

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/1 17:43
 * ================================================
 */

@Composable
public fun rememberSavableStateHolder(): SaveableStateHolder = rememberSaveable(saver = SaveableStateHolderImpl.Saver) { SaveableStateHolderImpl() }
    .apply { parentSaveableStateRegistry = LocalSaveableStateRegistry.current }

private class SaveableStateHolderImpl(
    private val referenceId: String = Uuid.random().toHexString(),
    private val savedStates: MutableMap<Any, Map<String, List<Any?>>> = mutableMapOf(),
) : SaveableStateHolder {
    private val registries = mutableScatterMapOf<Any, SaveableStateRegistry>()
    var parentSaveableStateRegistry: SaveableStateRegistry? = null
    private val canBeSaved: (Any) -> Boolean = {
        parentSaveableStateRegistry?.canBeSaved(it) ?: true
    }

    @Composable
    override fun SaveableStateProvider(key: Any, content: @Composable () -> Unit) {
        ReusableContent(key) {
            val registry = remember {
                require(canBeSaved(key)) {
                    "Type of the key $key is not supported. On Android you can only use types " +
                        "which can be stored inside the Bundle."
                }
                SaveableStateRegistryWrapper(
                    base = SaveableStateRegistry(restoredValues = savedStates[key], canBeSaved),
                )
            }
            CompositionLocalProvider(
                LocalSaveableStateRegistry provides registry,
                LocalSavedStateRegistryOwner provides registry,
                content = content,
            )
            DisposableEffect(Unit) {
                require(key !in registries) { "Key $key was used multiple times " }
                savedStates -= key
                registries[key] = registry
                onDispose {
                    if (registries.remove(key) === registry) {
                        registry.saveTo(savedStates, key)
                    }
                }
            }
        }
    }

    private fun saveAll(): MutableMap<Any, Map<String, List<Any?>>>? {
        val map = savedStates
        registries.forEach { key, registry -> registry.saveTo(map, key) }
        return map.ifEmpty { null }
    }

    private fun saveReference(): String {
        logStateHolderValue("save holder", this)
        val states = saveAll()
        logStateHolderValue("save referenceId", referenceId)
        logStateHolderValue("save states", states)
        if (states.isNullOrEmpty()) {
            stateStorage.remove(referenceId)
        } else {
            stateStorage.putString(referenceId, Json.encodeToString(states.toJson()))
        }
        return referenceId
    }

    override fun toString(): String = "top.kagg886.pmf.util.SaveableStateHolderImpl@${hashCode()} - ${saveAll()}"

    override fun removeState(key: Any) {
        if (registries.remove(key) == null) {
            savedStates -= key
        }
    }

    private fun SaveableStateRegistry.saveTo(
        map: MutableMap<Any, Map<String, List<Any?>>>,
        key: Any,
    ) {
        val savedData = performSave()
        if (savedData.isEmpty()) {
            map -= key
        } else {
            map[key] = savedData
        }
    }

    companion object {
        private val stateStorage by lazy { SystemConfig.getConfig("saveable_state_holder") }

        val Saver: Saver<SaveableStateHolderImpl, String> =
            Saver(
                save = {
                    logStateHolderValue("Saver.save input", it)
                    it.saveReference()
                },
                restore = { referenceId ->
                    logStateHolderValue("Saver.restore referenceId", referenceId)
                    val restored = stateStorage.getStringOrNull(referenceId)
                    logStateHolderValue("Saver.restore stored data", restored)
                    SaveableStateHolderImpl(
                        referenceId = referenceId,
                        savedStates = restored?.toSavedStates() ?: mutableMapOf(),
                    )
                },
            )
    }
}

private fun Map<Any, Map<String, List<Any?>>>.toJson(): JsonElement = JsonObject(
    mapNotNull { (contentKey, registryState) ->
        logStateHolderValue("save contentKey", contentKey)
        logStateHolderValue("save registryState", registryState)
        val state = registryState.toJsonObject() ?: return@mapNotNull null
        contentKey.toString() to state
    }.toMap(),
)

private fun Map<String, List<Any?>>.toJsonObject(): JsonObject? {
    val encoded = mapNotNull { (key, values) ->
        logStateHolderValue("save registry key", key)
        logStateHolderValue("save registry values", values)
        val encodedValues = values.mapNotNull { it.toJsonValue() }
        if (encodedValues.isEmpty() && values.isNotEmpty()) {
            null
        } else {
            key to JsonArray(encodedValues)
        }
    }.toMap()
    return if (encoded.isEmpty()) null else JsonObject(encoded)
}

private fun Any?.toJsonValue(): JsonElement? {
    logStateHolderValue("save value", this)
    return when (this) {
        null -> JsonObject(mapOf("type" to JsonPrimitive("null")))

        is Boolean -> JsonObject(mapOf("type" to JsonPrimitive("boolean"), "value" to JsonPrimitive(this)))

        is Int -> JsonObject(mapOf("type" to JsonPrimitive("int"), "value" to JsonPrimitive(this)))

        is Long -> JsonObject(mapOf("type" to JsonPrimitive("long"), "value" to JsonPrimitive(this)))

        is Float -> JsonObject(mapOf("type" to JsonPrimitive("float"), "value" to JsonPrimitive(this)))

        is Double -> JsonObject(mapOf("type" to JsonPrimitive("double"), "value" to JsonPrimitive(this)))

        is String -> JsonObject(mapOf("type" to JsonPrimitive("string"), "value" to JsonPrimitive(this)))

        is MutableState<*> -> {
            val encodedValue = value.toJsonValue() ?: return null
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("mutable_state"),
                    "value" to encodedValue,
                ),
            )
        }

        is List<*> -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("list"),
                "value" to JsonArray(mapNotNull { it.toJsonValue() }),
            ),
        )

        is Map<*, *> -> {
            val entries = mapNotNull { (key, value) ->
                val encodedKey = key.toJsonValue() ?: return@mapNotNull null
                val encodedValue = value.toJsonValue() ?: return@mapNotNull null
                JsonObject(
                    mapOf(
                        "key" to encodedKey,
                        "value" to encodedValue,
                    ),
                )
            }
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("map"),
                    "value" to JsonArray(entries),
                ),
            )
        }

        else -> {
            encodePlatformSaveableValue(this) ?: run {
                logStateHolderValue("skip unsupported value", this)
                null
            }
        }
    }
}

private fun String.toSavedStates(): MutableMap<Any, Map<String, List<Any?>>> = runCatching {
    Json.parseToJsonElement(this).jsonObject.mapValues { (_, registryState) ->
        registryState.jsonObject.mapValues { (_, values) ->
            values.jsonArray.map { it.toSavedValue() }
        }
    }.toMutableMap<Any, Map<String, List<Any?>>>()
}.getOrElse {
    mutableMapOf()
}

private fun JsonElement.toSavedValue(): Any? {
    logStateHolderValue("restore json value", this)
    val obj = jsonObject
    val value = obj["value"] ?: JsonNull
    val restored = when (obj["type"]?.jsonPrimitive?.contentOrNull) {
        "null" -> null

        "boolean" -> value.jsonPrimitive.boolean

        "int" -> value.jsonPrimitive.int

        "long" -> value.jsonPrimitive.contentOrNull?.toLong()

        "float" -> value.jsonPrimitive.float

        "double" -> value.jsonPrimitive.double

        "string" -> value.jsonPrimitive.contentOrNull.orEmpty()

        "mutable_state" -> mutableStateOf(value.toSavedValue())

        "list" -> value.jsonArray.map { it.toSavedValue() }

        "map" -> buildMap {
            value.jsonArray.forEach { item ->
                val pair = item.jsonObject
                put(pair.getValue("key").toSavedValue(), pair.getValue("value").toSavedValue())
            }
        }

        "platform" -> decodePlatformSaveableValue(obj)

        else -> null
    }
    logStateHolderValue("restore value", restored)
    return restored
}

private fun logStateHolderValue(stage: String, value: Any?) {
    stateHolderLogger.i {
        "$stage: class=${value?.let { it::class.toString() } ?: "null"}, toString=$value"
    }
}
