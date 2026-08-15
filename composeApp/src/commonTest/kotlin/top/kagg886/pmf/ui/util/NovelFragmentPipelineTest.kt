package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.kagg886.pmf.translate.SentenceTranslationState

/**
 * 用真实轻小说选文验证片段配对协议全链路：
 * 切句 → 句内切块 → 请求文本 → `原文⇔译文` 配对 → 状态合并 → 标点拼回。
 */
class NovelFragmentPipelineTest {
    private val excerpt =
        """
        ある夏の日。午前の開店前の時間に、小鳩さんが俺としろはの食堂にやってきた。
        「すみません、まだ開店前——あっ、爺さんか。どうしたんだ、こんな時間に？」
        「うむ……羽未は、いないのだな？」
        「羽未ちゃんならどこか遊びに行ってるんだけど、どうしたの？」
        小鳩さんが来たことに気づき、しろはも店の裏から出てきた。
        「二人しかいないのならちょうどいい。実は……少し話というか、相談したいことがあってな」
        相談？ と俺としろはは首を傾げる。小鳩さんから相談を持ち掛けてくることはかなり珍しい。
        とりあえず、俺は小鳩さんを店内に入れ、しろはは3人分のお茶を運んできて、俺の隣に座った。
        「おじーちゃん、相談って、なに？」
        「ふむ……実は今年の夏鳥の役なんだが、羽未にやってもらえないのだろうかと」
        「羽未に？」
        夏鳥の役は、この島で毎年行われるお盆のお祭り——夏鳥の儀の巫女のことだ。島にとっては大事な儀式だから、その巫女の役は大役ともいえる。基本的には島の女の子に毎年やってもらっていて、昔しろはもそれを担当したことがある。だからいつか、羽未の名が候補に上がっても全然不思議ではないと思っていた。
        ただ、羽未はまだ14歳で、今年の誕生日で15歳になるところだ。
        「まだ少し早いんじゃないか？ 来年羽未が高校に上がってからでも遅くないと思うけど」
        「うむ……それは確かにそうだが……」
        「羽未ちゃんがいないなら、私は全然なにも」
        「そうだな……羽未の意見も、大事だな……」
        「今年の候補になにか問題でもあったのか？ 去年やってた子は？」
        「いや、そこは別に問題になっていないが……なんというか……うむ……」
        これはまた珍しいことに、小鳩さんが何かを渋っている。昔はなりふり構わず、結構強引にことを進めようとする頑固な爺さんのは、俺が一番よく知っている。
        """.trimIndent()

    private fun buildFragments(): List<NovelFragmentSpan> = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(excerpt))).flatMap { it.fragments }

    @Test
    fun testRealLightNovelFragmentStructure() {
        val index = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(excerpt)))
        val fragments = index.flatMap { it.fragments }

        assertTrue(index.size >= 15, "句子数应足够（实际 ${index.size}）")
        assertTrue(fragments.size >= 40, "片段数应足够（实际 ${fragments.size}）")

        // 全部片段都能在原文中定位，且区间按顺序不重叠
        val positioned = positionNovelFragments(excerpt, fragments)
        assertEquals(fragments.size, positioned.size, "所有片段应定位成功")
        assertTrue(
            positioned.zipWithNext().all { (a, b) -> a.end <= b.start },
            "片段区间应按顺序不重叠",
        )

        // 对话框内多句（「…爺さんか。どうしたんだ、こんな時間に？」）作为一个句子切块
        val dialogueSentence =
            index.first { it.original.contains("まだ開店前") }
        assertTrue(dialogueSentence.original.startsWith("「"), "对话应整体成句: ${dialogueSentence.original}")
        val dialogueFragments = dialogueSentence.fragments.map { it.original }
        assertEquals("すみません", dialogueFragments.first(), "首个片段应为顿号前的块")
        assertTrue(dialogueFragments.last().contains("こんな時間に"), "末片段应含对话结尾")
    }

    @Test
    fun testRealLightNovelFullMatchRoundTrip() {
        val fragments = buildFragments()

        // 模拟模型理想响应：每行一个片段译文（按行号对齐）
        val lines = fragments.map { "译${it.id}" }
        val chunk = NovelSentenceChunk(fragments.map { it.id }, NOVEL_CHUNK_MARKER, fragments)
        val merged = mergeFragmentStates(emptyMap(), chunk, lines, final = true)

        assertEquals(fragments.size, merged.size)
        assertTrue(
            merged.values.all { it is SentenceTranslationState.Complete },
            "全量匹配应全部 Complete",
        )
    }

    @Test
    fun testRealLightNovelEchoAndCountMismatch() {
        val fragments = buildFragments()
        val echoFragment = fragments.first { "しろは" in it.translationSource }

        // 行数与片段数一致，但模型对专名回显（原样返回）
        val echoLines = fragments.map { if (it.id == echoFragment.id) it.translationSource else "译${it.id}" }
        val chunk = NovelSentenceChunk(fragments.map { it.id }, NOVEL_CHUNK_MARKER, fragments)
        val merged = mergeFragmentStates(emptyMap(), chunk, echoLines, final = true)

        assertEquals(
            SentenceTranslationState.Complete(echoFragment.original),
            merged[echoFragment.id],
            "专名回显应显示原文且不标红",
        )
        // 模型合并/漏行：非空行数 ≠ 片段数 → 整 chunk 失败（绝不索引硬配对），由自动重试逐片段恢复
        val missingLines = fragments.dropLast(1).map { "译${it.id}" }
        val mismatched = mergeFragmentStates(emptyMap(), chunk, missingLines, final = true)
        assertEquals(fragments.size, mismatched.size)
        assertTrue(
            mismatched.values.all { it is SentenceTranslationState.Failed },
            "行数不匹配应整 chunk 标失败",
        )
    }

    @Test
    fun testRealLightNovelChunkSourceTextAndCoverage() {
        val index = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(excerpt)))
        val sentences = index.associateBy { it.id }
        val fragments = index.flatMap { it.fragments }
        val chunks =
            buildNovelSentenceChunks(
                fragments.associateBy { it.id },
                sentences,
                mapOf(0 to excerpt),
                fragments.map { it.id }.toSet(),
            )

        assertTrue(chunks.isNotEmpty())
        for (chunk in chunks) {
            assertTrue(chunk.sourceText.contains(NOVEL_CHUNK_MARKER), "请求应含片段翻译指令")
            assertTrue(chunk.sourceText.contains("每行一个片段"), "指令应说明每行一个片段")
            assertTrue(chunk.sourceText.contains(excerpt.take(30)), "请求应携带段落上下文")
            assertTrue(chunk.fragmentIds.isNotEmpty())
            // 请求行 = 客户端切好的片段（不再让模型自行按顿号切分）
            for (fragment in chunk.fragments.take(3)) {
                assertTrue(
                    chunk.sourceText.contains(fragment.translationSource.trim()),
                    "请求应包含片段行: ${fragment.translationSource}",
                )
            }
            // 每请求最多 NOVEL_TRANSLATION_CHUNK_SIZE 句（行）
            assertTrue(
                chunk.fragments.map { it.sentenceId }.distinct().size <= NOVEL_TRANSLATION_CHUNK_SIZE,
                "每请求句子数应受限",
            )
        }
        // 每片段恰好进入一个 chunk
        assertEquals(fragments.size, chunks.sumOf { it.fragmentIds.size })
    }

    @Test
    fun testRetryIsSingleFragmentWithoutContext() {
        // 修复：重试 = 单个失败片段 + 无上下文（1 行输入必然 1 行输出，模型无法合并/漏行），
        // 且不带上下文避免"句上下文 + 单片段"的整句含义污染；
        // retry=true 走专用重试通道并绕过调度器缓存（每次点击重试都真实发起请求）。
        val index = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(excerpt)))
        val fragments = index.flatMap { it.fragments }
        val target = fragments.first { "しろは" in it.translationSource }
        val retrySource =
            buildNovelSentenceChunkSource(null, listOf(target.translationSource.trim()))
        assertTrue(retrySource.contains(NOVEL_CHUNK_MARKER), "重试请求应含翻译指令")
        assertTrue(retrySource.contains(target.translationSource.trim()), "重试请求应含待译片段")
        assertTrue(
            !retrySource.contains(excerpt.take(30)),
            "重试请求不应携带段落/句子上下文",
        )
        // 行数恒为 1：模型不可能合并出 lineCountMismatch
        assertEquals(
            1,
            retrySource.lines().count { it.isNotBlank() && !it.contains(NOVEL_CHUNK_MARKER) },
        )
    }
}
