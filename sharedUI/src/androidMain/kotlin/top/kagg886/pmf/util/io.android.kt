package top.kagg886.pmf.util

import android.annotation.SuppressLint
import android.app.Application
import okio.Path
import okio.Path.Companion.toPath

actual val dataPath: Path by lazy {
    currentApplication().filesDir.absolutePath.toPath()
}

actual val cachePath: Path by lazy {
    currentApplication().cacheDir.absolutePath.toPath()
}

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
fun currentApplication(): Application {
    val activityThread = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Application
    }.getOrNull()
    if (activityThread != null) return activityThread

    val appGlobals = runCatching {
        Class.forName("android.app.AppGlobals")
            .getDeclaredMethod("getInitialApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Application
    }.getOrNull()
    return requireNotNull(appGlobals) { "Application is not available" }
}
