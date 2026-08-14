package top.kagg886.pmf.translate

import androidx.compose.ui.text.intl.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageDetectorTest {
    private val zh = Locale("zh-CN")
    private val zhTw = Locale("zh-TW")
    private val en = Locale("en-US")

    @Test
    fun testPureChineseIsNotForeign() {
        assertFalse(LanguageDetector.isForeign("这是一段纯中文测试。", zh))
        assertFalse(LanguageDetector.isForeign("繁體中文測試", zhTw))
    }

    @Test
    fun testChineseWithKanaIsForeign() {
        assertTrue(LanguageDetector.isForeign("これは日本語です", zh))
        assertTrue(LanguageDetector.isForeign("中文与カタカナ混排", zh))
    }

    @Test
    fun testChineseWithLatinIsForeign() {
        assertTrue(LanguageDetector.isForeign("VOCALOID 初音未来", zh))
        assertTrue(LanguageDetector.isForeign("C89 新刊", zh))
    }

    @Test
    fun testPureEnglishIsNotForeign() {
        assertFalse(LanguageDetector.isForeign("This is an English sentence.", en))
        assertFalse(LanguageDetector.isForeign("café déjà vu", en))
    }

    @Test
    fun testEnglishWithCjkIsForeign() {
        assertTrue(LanguageDetector.isForeign("This is 中文 mixed.", en))
        assertTrue(LanguageDetector.isForeign("こんにちは", en))
    }

    @Test
    fun testChineseWithHangulCyrillicArabicIsForeign() {
        assertTrue(LanguageDetector.isForeign("안녕하세요", zh))
        assertTrue(LanguageDetector.isForeign("Привет", zh))
        assertTrue(LanguageDetector.isForeign("مرحبا", zh))
    }

    @Test
    fun testHalfWidthKatakanaIsForeignForChinese() {
        assertTrue(LanguageDetector.isForeign("ｺﾝﾆﾁﾊ", zh))
    }

    @Test
    fun testChineseFullWidthPunctuationIsNotForeign() {
        assertFalse(LanguageDetector.isForeign("这是，测试！？", zh))
    }

    @Test
    fun testEmptyAndPunctuationOnlyAreNotForeign() {
        assertFalse(LanguageDetector.isForeign("", zh))
        assertFalse(LanguageDetector.isForeign("   ", en))
        assertFalse(LanguageDetector.isForeign("！！！……", zh))
        assertFalse(LanguageDetector.isForeign("123456", en))
        assertFalse(LanguageDetector.isForeign("123456", zh))
    }

    @Test
    fun testTargetLanguageName() {
        assertEquals("中文", LanguageDetector.targetLanguageName(zh))
        assertEquals("繁體中文", LanguageDetector.targetLanguageName(zhTw))
        assertEquals("English", LanguageDetector.targetLanguageName(en))
    }
}
