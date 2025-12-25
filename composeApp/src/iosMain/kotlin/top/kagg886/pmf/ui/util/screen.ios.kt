package top.kagg886.pmf.ui.util
import androidx.compose.runtime.Composable
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIUserInterfaceSizeClassRegular

@get:Composable
actual val useWideScreenMode: Boolean
    get() {
        val traitCollection = LocalUIViewController.current.traitCollection
        return traitCollection.horizontalSizeClass == UIUserInterfaceSizeClassRegular
    }
