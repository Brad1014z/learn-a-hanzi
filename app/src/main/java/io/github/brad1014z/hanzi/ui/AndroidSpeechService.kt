package io.github.brad1014z.hanzi.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import io.github.brad1014z.hanzi.engine.speech.SpeechService
import io.github.brad1014z.hanzi.engine.speech.ttsLocaleTag
import java.util.Locale

/**
 * Android TextToSpeech implementation of the pure SpeechService interface (specs 01/06).
 * Init is async; until ready — or if no engine/voice exists for the language — the app
 * degrades gracefully to visual-only (spec 01: pronunciation is never a hard blocker).
 * Phase 3 swaps in the pre-generated-audio implementation behind the same interface.
 */
class AndroidSpeechService(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit = {},
) : SpeechService {

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        onReadyChanged(ready)
    }

    override fun speak(text: String, lang: String) {
        if (!isAvailable(lang)) return
        tts.language = locale(lang)
        // QUEUE_FLUSH: a new tap replaces the current utterance — debounce per spec 07.
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hanzi-$text")
    }

    override fun isAvailable(lang: String): Boolean =
        ready && tts.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE

    private fun locale(lang: String): Locale = Locale.forLanguageTag(ttsLocaleTag(lang))

    fun release() {
        tts.stop()
        tts.shutdown()
    }
}
