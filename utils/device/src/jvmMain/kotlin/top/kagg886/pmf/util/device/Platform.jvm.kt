package top.kagg886.pmf.util.device

public actual val Platform.Companion.current: Platform
    get() {
        val osName = System.getProperty("os.name")
        return when {
            osName.startsWith("Windows", ignoreCase = true) -> Windows
            osName.startsWith("Linux", ignoreCase = true) -> Linux
            osName.startsWith("Mac OS", ignoreCase = true) -> MacOS
            else -> error("Unsupported JVM operating system: $osName")
        }
    }
