package top.kagg886.pmf.util

import okio.BufferedSink
import okio.BufferedSource
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.Sink
import okio.Source

public fun Path.useFileSystem(fs: FileSystem = FileSystem.SYSTEM, scope: FileSystemScope.() -> Unit): Unit =
    FileSystemScope(this, fs).scope()

public class FileSystemScope internal constructor(public val base: Path, public val fs: FileSystem) {
    @PublishedApi
    internal fun Path.baseCurrentDir(): Path = if (base.isAbsolute) base else this.resolve(base)

    public fun canonicalize(): Path = fs.canonicalize(base)

    public fun metadata(): FileMetadata = fs.metadata(base)

    public fun metadataOrNull(): FileMetadata? = fs.metadataOrNull(base)

    public fun exists(): Boolean = fs.exists(base)

    public fun list(): List<Path> = fs.list(base)

    public fun listOrNull(): List<Path>? = fs.listOrNull(base)

    public fun listRecursively(): Sequence<Path> = fs.listRecursively(base)

    public fun openReadOnly(): FileHandle = fs.openReadOnly(base)

    public fun openReadWrite(): FileHandle = fs.openReadWrite(base, mustCreate = true)

    public fun source(): Source = fs.source(base)

    public inline fun <T> read(action: BufferedSource.() -> T): T = fs.read(base, action)

    public fun sink(): Sink = fs.sink(base, mustCreate = true)

    public inline fun <T> write(action: BufferedSink.() -> T): T = fs.write(base, mustCreate = true, action)

    public fun appendingSink(): Sink = fs.appendingSink(base, mustExist = true)

    public fun createDirectory(): Unit = fs.createDirectory(base, mustCreate = true)

    public fun createDirectories(): Unit = fs.createDirectories(base, mustCreate = true)

    public fun copy(target: Path) {
        val resolvedTarget = target.baseCurrentDir()
        resolvedTarget.requireExist()
        fs.copy(base, resolvedTarget)
    }

    public fun atomicMove(target: Path) {
        val resolvedTarget = target.baseCurrentDir()
        resolvedTarget.requireExist()
        fs.atomicMove(base, resolvedTarget)
    }

    public fun delete(): Unit = fs.delete(base, mustExist = true)
    public fun deleteRecursively(): Unit = fs.deleteRecursively(base, mustExist = true)
    public fun createSymlink(target: Path): Unit = fs.createSymlink(base, target.baseCurrentDir())

    private fun Path.requireExist() {
        if (fs.exists(this)) {
            throw IOException("Target already exists: $this")
        }
    }
}
