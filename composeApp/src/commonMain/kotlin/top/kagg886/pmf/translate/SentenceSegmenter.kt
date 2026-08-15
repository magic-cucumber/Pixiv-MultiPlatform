package top.kagg886.pmf.translate

/**
 * 按语义终结符对文本做句子分割，并针对日本轻小说排版做专门适配。
 *
 * - 支持中/日/英常用句末标点、换行与省略号；
 * - 连续省略号（…‥）归并为一次边界；
 * - 句末闭引号/括号/地板括号等轻小说常用符号会并入当前句；
 * - 英文句点仅在后面是空白且前一位不是数字时视为句子边界，避免误切小数与缩写。
 */
object SentenceSegmenter {
    private val terminators = "。．！？!?；;…‥、，"
    private val ellipsis = setOf('…', '‥')
    private val closers =
        setOf(
            '」', '』', '】', '〉', '》', '〕', '〗', '〙', '〛', '⌋', '⌉',
            '”', '’', '"', '）', ')', ']', '｝', '}', '＞', '>',
        )

    fun split(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            current.append(c)
            val isBoundary = c in terminators || c == '\n' || isEnglishPeriodBoundary(text, i)
            if (isBoundary) {
                if (c in ellipsis) {
                    while (i + 1 < text.length && text[i + 1] in ellipsis) {
                        current.append(text[++i])
                    }
                }
                if (c != '\n') {
                    while (i + 1 < text.length && text[i + 1] in closers) {
                        current.append(text[++i])
                    }
                }
                result.add(current.toString().trim())
                current.clear()
            }
            i++
        }
        if (current.isNotBlank()) {
            result.add(current.toString().trim())
        }
        return result.filter { it.isNotBlank() }
    }

    private fun isEnglishPeriodBoundary(text: String, index: Int): Boolean {
        if (text[index] != '.') return false
        if (index > 0 && text[index - 1].isDigit()) return false
        if (index + 1 < text.length && text[index + 1] == '.') return false
        return index + 1 >= text.length || text[index + 1].isWhitespace()
    }
}
