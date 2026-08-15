package top.kagg886.pmf.ui.util

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NovelSentenceIndexTest {
    private val nodes =
        listOf(
            NovelNodeElement.Title("第一章。"),
            NovelNodeElement.Plain("第一句。第二句！"),
            NovelNodeElement.NewPage(1),
            NovelNodeElement.UploadImage("https://example.com/a.png", Size(100f, 50f)),
            NovelNodeElement.Plain("第三句"),
        )
    private val nodeTexts: Map<Int, String> =
        mapOf(
            0 to "第一章。",
            1 to "第一句。第二句！",
            4 to "第三句",
        )

    @Test
    fun testBuildNovelSentenceIndexNumbersOnlyTextNodes() {
        val index = buildNovelSentenceIndex(nodes)
        assertEquals(
            listOf(
                NovelSentenceSpan(0, 0, "第一章。", listOf(NovelFragmentSpan(0, 0, 0, "第一章", "", "。"))),
                NovelSentenceSpan(1, 1, "第一句。", listOf(NovelFragmentSpan(1, 1, 1, "第一句", "", "。"))),
                NovelSentenceSpan(2, 1, "第二句！", listOf(NovelFragmentSpan(2, 2, 1, "第二句", "", "！"))),
                NovelSentenceSpan(3, 4, "第三句", listOf(NovelFragmentSpan(3, 3, 4, "第三句", "", ""))),
            ),
            index,
        )
    }

    @Test
    fun testFragmentsSplitAtCommaWithBracketAwareness() {
        val index =
            buildNovelSentenceIndex(
                listOf(
                    NovelNodeElement.Plain("しろは、お早う～。"),
                ),
            )
        val sentence = index.single()
        // ～ 是句尾标点（随 。 一起作为句尾间隙拼回），不进入片段
        assertEquals(listOf("しろは", "お早う"), sentence.fragments.map { it.original })
        assertEquals(listOf("しろは", "お早う"), sentence.fragments.map { it.translationSource })
        // 括号组内的顿号不切分
        val bracket =
            buildNovelSentenceIndex(
                listOf(NovelNodeElement.Plain("と⌈こんにちは、元気⌋と話した。")),
            ).single()
        assertEquals(1, bracket.fragments.size)
    }

    @Test
    fun testPositionNovelFragmentsPreservesWhitespaceBetweenFragments() {
        val text = "しろは、お早う～。\n元気、ですか？"
        val fragments = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(text))).flatMap { it.fragments }
        val positions = positionNovelFragments(text, fragments)
        assertEquals(fragments.size, positions.size)
        // 间隙（、～。\n）在定位之间保留
        val firstGap = text.substring(positions[0].end, positions[1].start)
        assertTrue(firstGap.contains("、"), "片段间间隙应保留顿号: $firstGap")
    }

    @Test
    fun testPositionNovelFragmentsWithDuplicateText() {
        val text = "はい、はい、違う！"
        val fragments = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(text))).flatMap { it.fragments }
        val positions = positionNovelFragments(text, fragments)
        assertEquals(listOf(0, 3, 6), positions.map { it.start })
        assertEquals(listOf("はい", "はい", "違う"), positions.map { it.fragment.original })
    }

    @Test
    fun testLightNovelPunctuationIsSplitLocallyAndReattached() {
        val fragment = NovelFragmentSpan(0, 0, 0, "お早う～", "「", "")
        assertEquals("", fragment.leadingPunctuation)
        assertEquals("お早う", fragment.translationSource)
        assertEquals("～", fragment.trailingPunctuation)
        assertEquals("早上好～", reattachNovelFragmentPunctuation(fragment, "早上好。"))

        val comma = NovelFragmentSpan(1, 0, 0, "、こんにちは、", "", "")
        assertEquals("、", comma.leadingPunctuation)
        assertEquals("こんにちは", comma.translationSource)
        assertEquals("、", comma.trailingPunctuation)
        assertEquals("、Hello、", reattachNovelFragmentPunctuation(comma, "Hello"))
    }

    @Test
    fun testPunctuationOnlySegmentIsNotSentToAi() {
        val index =
            buildNovelSentenceIndex(
                listOf(
                    NovelNodeElement.Plain("第一句。\n……⌋\n第二句！"),
                ),
            )
        assertEquals(listOf("第一句。", "第二句！"), index.map { it.original })
        assertEquals(2, index.flatMap { it.fragments }.size)
    }

    @Test
    fun testPunctuationOnlyBracketGroupIsNotSentToAi() {
        // ⌈……⌋ 整组为纯标点：核心为空，整句不应进入翻译索引
        val index =
            buildNovelSentenceIndex(
                listOf(NovelNodeElement.Plain("彼女は⌈……⌋と呟いた。\n⌈……⌋")),
            )
        assertEquals(listOf("彼女は⌈……⌋と呟いた。"), index.map { it.original })
        val fragment = index.single().fragments.single()
        assertTrue("……" !in fragment.translationSource, "纯标点括号组不应发给 AI")
        assertTrue(fragment.translationSource.contains("彼女は"), "核心应保留组外文本")
    }

    @Test
    fun testMidSentenceDialogueBracketsAreTokenizedAndRestored() {
        // 开括号在句首被剥离、闭括号留在片段核心：闭括号必须 token 化，防止模型丢弃引号
        val index =
            buildNovelSentenceIndex(
                listOf(NovelNodeElement.Plain("⌈こんにちは⌋と彼女は言った。")),
            )
        val fragment = index.single().fragments.single()
        val source = fragment.translationSource
        assertTrue("⌋" !in source, "核心不应包含裸 ⌋: $source")
        assertTrue(source.contains("こんにちは"), "核心应保留对话内文: $source")
        val closeToken = fragment.protection.tokens.single { it.replacement == "⌋" }
        // 句首 ⌈ / 句尾 。 由渲染层作为原文间隙拼回；reattach 只处理片段自身标点与 token 还原
        assertEquals(
            "你好⌋她说",
            reattachNovelFragmentPunctuation(fragment, "你好" + closeToken.token + "她说"),
        )
    }

    @Test
    fun testCrossFragmentBracketPairing() {
        // 「 在句首被剥离，」留在第二个片段核心：跨片段配对 → token 化 → 还原
        val index =
            buildNovelSentenceIndex(
                listOf(NovelNodeElement.Plain("「はい、そうですね」と言った。")),
            )
        val sentence = index.single()
        assertEquals(listOf("はい", "そうですね」と言った"), sentence.fragments.map { it.original })
        val second = sentence.fragments[1]
        assertTrue("」" !in second.translationSource, "」 应被 token 化: ${second.translationSource}")
        val closeToken = second.protection.tokens.single { it.replacement == "」" }
        assertEquals(
            "そうですね」她说",
            reattachNovelFragmentPunctuation(second, "そうですね" + closeToken.token + "她说"),
        )
    }

    @Test
    fun testDialogueGroupIsOneSentenceWithBracketsReattached() {
        // 括号内的句末标点不切句：整段对话作为一句，句首/句尾引号由拼回逻辑恢复
        val index =
            buildNovelSentenceIndex(listOf(NovelNodeElement.Plain("「行け！止まるな！」")))
        assertEquals(listOf("「行け！止まるな！」"), index.map { it.original })
        assertEquals(1, index.single().fragments.size)
        assertEquals("行け！止まるな", index.single().fragments.single().translationSource)
    }

    @Test
    fun testPositionNovelFragmentsKeepsDoubleNewlineParagraphBreak() {
        val text = "第一段。\n\n第二段！"
        val fragments = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(text))).flatMap { it.fragments }
        val positions = positionNovelFragments(text, fragments)
        assertEquals(2, positions.size)
        // 片段为剥离句尾标点的核心：间隙含句尾 。 与段落空行
        assertEquals("。\n\n", text.substring(positions[0].end, positions[1].start))
    }

    @Test
    fun testBuildNovelSentenceChunksGroupByNodeAndLimitSentences() {
        // id 3（node4）与 id 0/2（node0/1）分属不同节点，不跨节点组段
        val index = buildNovelSentenceIndex(nodes)
        val fragments = index.flatMap { it.fragments }
        val byId = fragments.associateBy { it.id }
        val sentences = index.associateBy { it.id }
        val chunks =
            buildNovelSentenceChunks(
                byId,
                sentences,
                nodeTexts,
                setOf(3, 0, 2),
                maxSentencesPerChunk = 2,
            )
        assertEquals(3, chunks.size)
        assertEquals(listOf(0), chunks[0].fragmentIds)
        assertEquals("第一章。", chunks[0].contextText)
        assertEquals(listOf(2), chunks[1].fragmentIds)
        assertEquals("第一句。第二句！", chunks[1].contextText)
        assertEquals(listOf(3), chunks[2].fragmentIds)
        assertEquals("第三句", chunks[2].contextText)

        // 同节点多句超过上限时按阅读顺序分片（按句子行数）
        val longNode = NovelNodeElement.Plain("一。二。三。四。五。六。七。")
        val longIndex = buildNovelSentenceIndex(listOf(longNode))
        val longFragments = longIndex.flatMap { it.fragments }
        val longChunks =
            buildNovelSentenceChunks(
                longFragments.associateBy { it.id },
                longIndex.associateBy { it.id },
                mapOf(0 to longNode.text),
                longFragments.map { it.id }.toSet(),
                maxSentencesPerChunk = 4,
            )
        assertEquals(listOf(0, 1, 2, 3), longChunks[0].fragments.map { it.id })
        assertEquals(listOf(4, 5, 6), longChunks[1].fragments.map { it.id })
    }

    @Test
    fun testChunkSourceHasContextHeaderAndFragmentInstruction() {
        val index = buildNovelSentenceIndex(nodes)
        val fragments = index.flatMap { it.fragments }
        val chunks =
            buildNovelSentenceChunks(
                fragments.associateBy { it.id },
                index.associateBy { it.id },
                nodeTexts,
                setOf(1, 2),
                withContext = true,
            )
        assertEquals(1, chunks.size)
        val source = chunks.single().sourceText
        assertTrue(source.contains("【上下文"), "应包含上下文头: $source")
        assertTrue(source.contains("第一句。第二句！"), "应包含段落上下文: $source")
        assertTrue(source.contains(NOVEL_CHUNK_MARKER), "应包含片段配对指令")
        assertTrue(source.contains("第一句"), "应包含句子行: $source")
        assertTrue(source.contains("第二句"), "应包含句子行: $source")
        // 不带上下文时仍保留指令（协议必需），只是没有上下文块
        val plain =
            buildNovelSentenceChunks(
                fragments.associateBy { it.id },
                index.associateBy { it.id },
                nodeTexts,
                setOf(1),
                withContext = false,
            )
        assertTrue(plain.single().sourceText.contains(NOVEL_CHUNK_MARKER))
        assertTrue(!plain.single().sourceText.contains("【上下文"))
    }

    @Test
    fun testStripNovelChunkContextRemovesEchoedContext() {
        val raw =
            "【上下文，仅参考，不要翻译或输出】 Context for reference only — do not translate or output it\n" +
                "第一句。第二句！\n" +
                "\n" +
                NOVEL_CHUNK_MARKER + "\n" +
                "しろは⇔白叶\n" +
                "お早う⇔早上好"
        assertEquals("しろは⇔白叶\nお早う⇔早上好", stripNovelChunkContext(raw))
        assertEquals("第一句\n第二句", stripNovelChunkContext("第一句\n第二句"))
    }

    @Test
    fun testStripNovelChunkContextIgnoresFakeMarkerLines() {
        // 小说原文包含近似标记（非完整行）时不应误剥离
        val raw = "【待翻译句子っぽい行\n" + NOVEL_CHUNK_MARKER + "\n你好"
        assertEquals("你好", stripNovelChunkContext(raw))
        // 模型多次回显上下文块：从最后一个完整标记行之后开始返回
        val doubleEcho = NOVEL_CHUNK_MARKER + "\n译1\n" + NOVEL_CHUNK_MARKER + "\n译2"
        assertEquals("译2", stripNovelChunkContext(doubleEcho))
    }

    @Test
    fun testChunkContextIsCapped() {
        val longContext = "あ".repeat(2000)
        val source = buildNovelSentenceChunkSource(longContext, listOf("句"))
        assertTrue(source.contains("あ".repeat(1500)), "上下文应保留到上限")
        assertTrue(!source.contains("あ".repeat(1501)), "超长上下文应被截断")
    }

    @Test
    fun testBracketTokenCapDoesNotCrash() {
        // 恶意构造的超长括号句：token 超限后原样保留括号，不崩溃
        val text = (1..300).joinToString("") { "（x）" } + "。"
        val fragments = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(text))).flatMap { it.fragments }
        val fragment = fragments.single()
        assertTrue(fragment.translationSource.isNotBlank())
        assertTrue(fragment.protection.tokens.size <= 256)
    }
}
