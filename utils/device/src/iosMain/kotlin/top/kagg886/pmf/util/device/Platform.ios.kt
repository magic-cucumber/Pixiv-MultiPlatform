package top.kagg886.pmf.util.device

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIUserInterfaceIdiomPhone

public actual val Platform.Companion.current: Platform
    get() = when (UIDevice.currentDevice.userInterfaceIdiom) {
        UIUserInterfaceIdiomPhone -> IPhoneOS
        UIUserInterfaceIdiomPad -> IPadOS
        else -> error("Unsupported Apple user-interface idiom")
    }
