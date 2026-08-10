package top.kagg886.pmf.util.nav3

internal data class NavScopePath<T : SerializableNavKey>(val routes: List<T>)

internal data class NavDisplayEntry<T : SerializableNavKey>(
    val key: T,
    val historyIndex: Int,
    val node: NavGraph.Node<T>,
    val childFrame: NavDisplayFrame<T>? = null,
)

internal data class NavDisplayFrame<T : SerializableNavKey>(
    val scope: NavScopePath<T>,
    val entries: List<NavDisplayEntry<T>>,
) {
    init {
        require(entries.isNotEmpty()) { "A NavDisplay frame cannot be empty" }
    }

    fun contentKeyReferenceCounts(): Map<String, Int> = buildMap {
        fun count(frame: NavDisplayFrame<T>) {
            frame.entries.forEach { entry ->
                val contentKey = entry.key.contentKey()
                put(contentKey, getOrElse(contentKey) { 0 } + 1)
                entry.childFrame?.let(::count)
            }
        }
        count(this@NavDisplayFrame)
    }

    fun entryContentKeys(): Set<String> = buildSet {
        fun collect(frame: NavDisplayFrame<T>) {
            frame.entries.forEach { entry ->
                add(frame.entryContentKey(entry))
                entry.childFrame?.let(::collect)
            }
        }
        collect(this@NavDisplayFrame)
    }
}

internal fun <T : SerializableNavKey> NavDisplayFrame<T>.entryContentKey(
    entry: NavDisplayEntry<T>,
): String = "${scope.routes.size}:${entry.historyIndex}:${entry.key.contentKey()}"

internal fun <T : SerializableNavKey> NavGraph<T>.project(
    backStack: List<T>,
): NavDisplayFrame<T> {
    require(backStack.isNotEmpty()) { "The navigation back stack cannot be empty" }
    val records = backStack.mapIndexed { index, key ->
        val path = resolvePath(key)
        require(path.last() == key) { "Back stack records must be visible leaf destinations: $key" }
        PathRecord(index, path)
    }
    return projectRecords(records, depth = 0, scope = emptyList())
}

private data class PathRecord<T : SerializableNavKey>(
    val historyIndex: Int,
    val path: List<T>,
)

private fun <T : SerializableNavKey> NavGraph<T>.projectRecords(
    records: List<PathRecord<T>>,
    depth: Int,
    scope: List<T>,
): NavDisplayFrame<T> {
    val entries = mutableListOf<NavDisplayEntry<T>>()
    var index = 0
    while (index < records.size) {
        val record = records[index]
        val key = record.path[depth]
        val node = nodeFor(record.path, depth)
        if (node is NavGraph.Route<T>) {
            var end = index + 1
            while (end < records.size && records[end].path.getOrNull(depth) == key) end++
            val childRecords = records.subList(index, end)
            entries += NavDisplayEntry(
                key = key,
                historyIndex = record.historyIndex,
                node = node,
                childFrame = projectRecords(childRecords, depth + 1, scope + key),
            )
            index = end
        } else {
            require(depth == record.path.lastIndex) {
                "Destination $key is not the leaf of ${record.path}"
            }
            entries += NavDisplayEntry(
                key = key,
                historyIndex = record.historyIndex,
                node = node,
            )
            index++
        }
    }
    return NavDisplayFrame(NavScopePath(scope), entries)
}
