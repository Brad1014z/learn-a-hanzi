package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.speech.ttsLocaleTag
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechLangTest {

    @Test
    fun `content lang tags map to concrete TTS locales`() {
        assertEquals("zh-CN", ttsLocaleTag("zh-Hans"))
        assertEquals("zh-CN", ttsLocaleTag("zh"))
        assertEquals("zh-TW", ttsLocaleTag("zh-Hant"))
        assertEquals("ja-JP", ttsLocaleTag("ja"))
        assertEquals("ko-KR", ttsLocaleTag("ko"))
        // Unknown tags pass through untouched.
        assertEquals("fr-FR", ttsLocaleTag("fr-FR"))
    }
}
