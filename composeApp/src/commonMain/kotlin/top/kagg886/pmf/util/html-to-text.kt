package top.kagg886.pmf.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * 将 HTML 文本转换为适合机器翻译的纯文本。
 *
 * - [com.fleeksoft.ksoup.nodes.Element] `br` 转换为换行；
 * - 块级标签（p/div/标题/列表项等）在内容结束后补换行；
 * - `a[href]` 在链接文本后追加 `(url)`，避免翻译后丢失超链接地址；
 * - 其余标签递归取文本，丢弃样式/结构标签。
 *
 * 纯文本输入会原样返回（仅去除首尾空白）。
 */
fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    val body = Ksoup.parse(html).body()
    val builder = StringBuilder()
    appendHtmlNodes(builder, body.childNodes())
    return builder.toString().trim()
}

private fun appendHtmlNodes(builder: StringBuilder, nodes: List<Node>) {
    for (node in nodes) {
        when (node) {
            is TextNode -> builder.append(node.text())

            is Element -> {
                when (node.tagName()) {
                    "br" -> builder.append('\n')

                    "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote" -> {
                        appendHtmlNodes(builder, node.childNodes())
                        builder.append('\n')
                    }

                    "a" -> {
                        val href = node.attr("href").trim()
                        val text = node.text().trim()
                        builder.append(text)
                        if (href.isNotEmpty() && href != text) {
                            builder.append(" ($href)")
                        }
                    }

                    else -> appendHtmlNodes(builder, node.childNodes())
                }
            }

            else -> {}
        }
    }
}
