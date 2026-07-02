@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "UNCHECKED_CAST")

package top.kagg886.pmf.util

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistryWrapper
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.os.use
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import co.touchlab.kermit.Logger
import java.util.UUID
import okio.buffer
import top.kagg886.pmf.PMFApplication
import top.kagg886.pmf.backend.dataPath


internal val state_holder_logger = Logger.withTag("ReferenceSavableStateHolder")

@Composable
actual fun rememberReferenceSavableStateHolder(): SaveableStateHolder =
    rememberSaveable(saver = ReferenceSaveableStateHolder.Saver) {
        ReferenceSaveableStateHolder()
    }.apply {
        parentSaveableStateRegistry = LocalSaveableStateRegistry.current
    }

private class ReferenceSaveableStateHolder(
    private val savedStates: MutableMap<Any, Map<String, List<Any?>>> = mutableMapOf(),
) : SaveableStateHolder {
    private val registries = mutableMapOf<Any, SaveableStateRegistry>()

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
                    base = SaveableStateRegistry(
                        restoredValues = savedStates[key],
                        canBeSaved = canBeSaved,
                    ),
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
                        val savedData = registry.performSave()
                        if (savedData.isEmpty()) {
                            savedStates -= key
                        } else {
                            savedStates[key] = savedData
                        }
                    }
                }
            }
        }
    }

    override fun removeState(key: Any) {
        if (registries.remove(key) == null) {
            savedStates -= key
        }
    }

    companion object {
        val Saver: Saver<ReferenceSaveableStateHolder, ReferenceStateParcelable> = Saver(
            save = { holder ->
                holder.registries.forEach { (key, registry) ->
                    val savedData = registry.performSave()
                    if (savedData.isEmpty()) {
                        holder.savedStates -= key
                    } else {
                        holder.savedStates[key] = savedData
                    }
                }

                if (holder.savedStates.isEmpty()) {
                    null
                } else {
                    ReferenceStateStore.save(holder.savedStates)
                }
            },
            restore = { reference ->
                ReferenceSaveableStateHolder(ReferenceStateStore.restore(reference))
            },
        )
    }
}

private class ReferenceStateParcelable(val referenceId: String) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString().orEmpty())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(referenceId)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ReferenceStateParcelable> {
        override fun createFromParcel(parcel: Parcel): ReferenceStateParcelable =
            ReferenceStateParcelable(parcel)

        override fun newArray(size: Int): Array<ReferenceStateParcelable?> = arrayOfNulls(size)
    }
}

private object ReferenceStateStore {
    private const val DIR_NAME = "saveable-state-holder"
    private const val FILE_SUFFIX = ".parcel"

    fun save(savedStates: MutableMap<Any, Map<String, List<Any?>>>): ReferenceStateParcelable {
        val referenceId = UUID.randomUUID().toString()
        val file = dataPath.resolve(DIR_NAME).resolve("$referenceId$FILE_SUFFIX")

        Parcel.obtain().use { parcel->
            parcel.writeValue(HashMap(savedStates))
            val bytes = parcel.marshall()

            state_holder_logger.i("saved state size: ${bytes.size.b}, content = $savedStates")

            file.parentFile()?.mkdirs()
            file.createNewFile()
            file.writeBytes(bytes)
        }

        return ReferenceStateParcelable(referenceId)
    }

    fun restore(parcelable: ReferenceStateParcelable): MutableMap<Any, Map<String, List<Any?>>> {
        val file = dataPath.resolve(DIR_NAME).resolve("${parcelable.referenceId}$FILE_SUFFIX")
        if (!file.exists()) {
            state_holder_logger.w("saveable state reference missing: ${parcelable.referenceId}")
            return mutableMapOf()
        }

        val bytes = file.source().buffer().use { it.readByteArray() }
        val data = try {
            Parcel.obtain().use {
                it.unmarshall(bytes, 0, bytes.size)
                it.setDataPosition(0)
                it.readValue(PMFApplication.getApp().classLoader) as? Map<Any, Map<String, List<Any?>>>
            }
        } catch (e: Exception) {
            state_holder_logger.w("savable state reference restore failed: ${parcelable.referenceId}\n${e.stackTraceToString()}")
            null
        }

        if (data == null) {
            state_holder_logger.w("savable state reference restore failed: ${parcelable.referenceId}, because api return empty value")
            return emptyMap<Any, Map<String, List<Any?>>>().toMutableMap()
        }


        state_holder_logger.i("saved state size: ${bytes.size.b}, content = $data")

        return data.toMutableMap()
    }
}
