package top.kagg886.pmf.translate

/**
 * 按语义终结符对文本做句子分割。
 *
 * 支持中/日/英常用句末标点与换行；句末的闭引号/括号会并入当前句，
 * 英文句点仅在后面是空白且前一位不是数字时视为句子边界，避免误切小数与缩写。
 */
object SentenceSegmenter {
    private val terminators = "。！？!?；;\n…"
    private val closers = setOf('」', '』', '”', '’', '）', ')', '】', '〉', '》', ']', '】')

    fun split(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            current.append(c)
            val isBoundary = c in terminators || isEnglishPeriodBoundary(text, i)
            if (isBoundary) {
                while (i + 1 < text.length && text[i + 1] in closers) {
                    current.append(text[++i])
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
