package top.kagg886.pmf.translate

import io.github.hatoyuze.deepseek.protocol.api.ChatChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class DeepseekTranslatorTest {
    @Test
    fun testContentDeltasDoNotRepeatHistoryPrefix() = runBlocking {
        val chunks = flowOf(
            ChatChunk.ContentDelta("我"),
            ChatChunk.ContentDelta("是"),
            ChatChunk.ContentDelta("Deepseek"),
            ChatChunk.ContentDelta("了"),
        )

        val result = chunks.contentDeltas().toList()

        assertEquals(listOf("我", "是", "Deepseek", "了"), result)
    }
}
