package top.kagg886.pmf.ui.util

/**
 * 计算每个正文节点所属的页号：以 [NovelNodeElement.NewPage] 为分页边界，
 * 首个分页标记之前的节点属于第 0 页。
 */
fun buildPageIndex(nodes: List<NovelNodeElement>): List<Int> {
    val result = ArrayList<Int>(nodes.size)
    var page = 0
    for (node in nodes) {
        result.add(page)
        if (node is NovelNodeElement.NewPage) page++
    }
    return result
}

/** 拼接某一页内 Plain/Title 节点的原文，作为该页的翻译请求文本。 */
fun buildPageText(nodes: List<NovelNodeElement>, pageIndex: List<Int>, page: Int): String = buildString {
    for ((index, node) in nodes.withIndex()) {
        if (pageIndex[index] != page) continue
        when (node) {
            is NovelNodeElement.Plain -> append(node.text).append('\n')
            is NovelNodeElement.Title -> append(node.text).append('\n')
            else -> {}
        }
    }
}.trim()
