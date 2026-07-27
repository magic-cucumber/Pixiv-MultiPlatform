package top.kagg886.pmf.util.device

/** The operating-system family hosting the application. */
public interface Platform {
    public companion object
}

/** A platform hosted by a Java virtual machine. */
public interface JavaPlatform : Platform

/** A platform compiled to a Kotlin/Native binary. */
public interface NativePlatform : Platform

/** A desktop platform. */
public interface Desktop : Platform

/** A mobile platform. */
public interface Mobile : Platform

/** An Apple platform, independent of its runtime. */
public interface Apple : Platform

public data object Windows : JavaPlatform, Desktop

public data object Linux : JavaPlatform, Desktop

public data object MacOS : JavaPlatform, Desktop, Apple

public data object Android : JavaPlatform, Mobile

public data object IPhoneOS : NativePlatform, Mobile, Apple

public data object IPadOS : NativePlatform, Mobile, Apple

/** The platform on which the current process is running. */
public expect val Platform.Companion.current: Platform
