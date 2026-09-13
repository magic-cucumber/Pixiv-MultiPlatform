package top.kagg886.pmf.backend

import com.russhwolf.settings.Settings
import com.russhwolf.settings.int
import com.russhwolf.settings.nullableInt

object DesktopWindowConfig : Settings by SystemConfig.getConfig("desktop-window") {
    var x by nullableInt("x")
    var y by nullableInt("y")
    var w by int("w",800)
    var h by int("h",600)
}
