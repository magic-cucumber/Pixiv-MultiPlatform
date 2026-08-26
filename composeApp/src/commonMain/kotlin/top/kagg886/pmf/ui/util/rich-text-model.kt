package top.kagg886.pmf.ui.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * 富文本结构化片段：HTML 解析后的文本/加粗/链接/换行单元。
 *
 * 复用 [HTMLRichText] 的 Ksoup 管线，把富文本拆成可翻译的片段——翻译时链接 URL、
 * 换行结构得以保留，仅替换文本内容；渲染时由同一个 annotated builder 还原为可点击链接。
 */
sealed interface RichSegment {
    data class Text(val text: String) : RichSegment

    data class Bold(val text: String) : RichSegment

    data class Link(val display: String, val url: String) : RichSegment

    data object Break : RichSegment
}

/** 解析 HTML 为结构化片段，处理 `a`/`strong`/`br` 与块级换行。 */
fun parseHtmlSegments(html: String): List<RichSegment> {
    if (html.isBlank()) return emptyList()
    val body = Ksoup.parse(html).body()
    return buildList { appendHtmlSegmentNodes(body.childNodes(), this) }
}

private fun appendHtmlSegmentNodes(nodes: List<Node>, out: MutableList<RichSegment>) {
    for (node in nodes) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotEmpty()) out += RichSegment.Text(text)
            }

            is Element -> {
                when (node.tagName()) {
                    "br" -> out += RichSegment.Break

                    "strong", "b" -> {
                        val text = node.text()
                        if (text.isNotEmpty()) out += RichSegment.Bold(text)
                    }

                    "a" -> {
                        val href = node.attr("href").trim()
                        val display = node.text().trim()
                        out += RichSegment.Link(display.ifEmpty { href }, href)
                    }

                    // 块级标签：内容结束后补换行
                    "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote" -> {
                        appendHtmlSegmentNodes(node.childNodes(), out)
                        out += RichSegment.Break
                    }

                    else -> appendHtmlSegmentNodes(node.childNodes(), out)
                }
            }

            else -> {}
        }
    }
}

/** 是否形如 URL（含协议或 `www.`），此类链接显示文本不做翻译。 */
fun isUrlLike(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("http://") ||
        trimmed.startsWith("https://") ||
        trimmed.startsWith("www.") ||
        "://" in trimmed
}

/**
 * 翻译富文本片段：保留链接 URL 与换行，翻译其中的文本。
 *
 * - 连续文本/加粗合并为一个块整体翻译（加粗在译文里扁平化为普通文本）；
 * - 链接保留原 URL，仅当显示文本含字母且非 URL 时才单独翻译；
 * - 任一块翻译失败（[translateBlock] 返回 null）则整体返回 null，由调用方回退原文。
 */
suspend fun translateRichSegments(
    segments: List<RichSegment>,
    translateBlock: suspend (String) -> String?,
): List<RichSegment>? {
    val result = mutableListOf<RichSegment>()
    val block = StringBuilder()

    suspend fun flushBlock(): Boolean {
        if (block.isEmpty()) return true
        val raw = block.toString()
        block.clear()
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            // 纯空白块保留原样，避免丢失段落缩进
            result += RichSegment.Text(raw)
            return true
        }
        val translated = translateBlock(trimmed) ?: return false
        // 保留前导/尾随空白，避免链接两侧空格在翻译后丢失
        val leading = raw.takeWhile { it.isWhitespace() }
        val trailing = raw.takeLastWhile { it.isWhitespace() }
        result += RichSegment.Text(leading + translated + trailing)
        return true
    }

    for (segment in segments) {
        when (segment) {
            is RichSegment.Text -> block.append(segment.text)

            is RichSegment.Bold -> block.append(segment.text)

            is RichSegment.Break -> {
                if (!flushBlock()) return null
                result += RichSegment.Break
            }

            is RichSegment.Link -> {
                if (!flushBlock()) return null
                val display =
                    if (isUrlLike(segment.display) || segment.display.none { it.isLetter() }) {
                        segment.display
                    } else {
                        translateBlock(segment.display) ?: return null
                    }
                result += RichSegment.Link(display, segment.url)
            }
        }
    }
    if (!flushBlock()) return null
    return result
}
