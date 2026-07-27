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

public class FileSystemScope internal constructor(public val path: Path, public val fs: FileSystem) {
    @PublishedApi
    internal fun Path.baseCurrentDir(): Path = if (path.isAbsolute) path else this.resolve(path)

    public fun canonicalize(): Path = fs.canonicalize(path)

    public fun metadata(): FileMetadata = fs.metadata(path)

    public fun metadataOrNull(): FileMetadata? = fs.metadataOrNull(path)

    public fun exists(): Boolean = fs.exists(path)

    public fun list(): List<Path> = fs.list(path)

    public fun listOrNull(): List<Path>? = fs.listOrNull(path)

    public fun listRecursively(): Sequence<Path> = fs.listRecursively(path)

    public fun openReadOnly(): FileHandle = fs.openReadOnly(path)

    public fun openReadWrite(): FileHandle = fs.openReadWrite(path, mustCreate = true)

    public fun source(): Source = fs.source(path)

    public inline fun <T> read(action: BufferedSource.() -> T): T = fs.read(path, action)

    public fun sink(): Sink = fs.sink(path, mustCreate = true)

    public inline fun <T> write(action: BufferedSink.() -> T): T = fs.write(path, mustCreate = true, action)

    public fun appendingSink(): Sink = fs.appendingSink(path, mustExist = true)

    public fun createDirectory(): Unit = fs.createDirectory(path, mustCreate = true)

    public fun createDirectories(): Unit = fs.createDirectories(path, mustCreate = true)

    public fun copy(target: Path) {
        val resolvedTarget = target.baseCurrentDir()
        checkTargetDoesNotExist(resolvedTarget)
        fs.copy(path, resolvedTarget)
    }

    public fun atomicMove(target: Path) {
        val resolvedTarget = target.baseCurrentDir()
        checkTargetDoesNotExist(resolvedTarget)
        fs.atomicMove(path, resolvedTarget)
    }

    public fun delete(): Unit = fs.delete(path, mustExist = true)
    public fun deleteRecursively(): Unit = fs.deleteRecursively(path, mustExist = true)
    public fun createSymlink(target: Path): Unit = fs.createSymlink(path, target.baseCurrentDir())

    private fun checkTargetDoesNotExist(target: Path) {
        if (fs.exists(target)) {
            throw IOException("Target already exists: $target")
        }
    }
}
