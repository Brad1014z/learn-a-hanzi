package io.github.brad1014z.hanzi.engine.speech

/**
 * Language-agnostic pronunciation interface (specs 01/06): all speech goes through
 * this, so Japanese/Korean later are new locales, not new designs (spec 09). Pure —
 * implementations live in platform layers (Android TextToSpeech now; the pre-generated
 * audio player in Phase 3, spec 12).
 */
interface SpeechService {
    /**
     * Speak [text] in [lang] (BCP-47, e.g. "zh-Hans"). Fire-and-forget; a new request
     * replaces any utterance in progress (spec 07: debounced, no overlapping audio).
     */
    fun speak(text: String, lang: String)

    /**
     * Whether speech for [lang] is currently available. When false, callers degrade
     * gracefully to visual-only (spec 01: pronunciation is never a hard blocker).
     */
    fun isAvailable(lang: String): Boolean

    /** A no-op implementation for tests and speech-less environments. */
    object Silent : SpeechService {
        override fun speak(text: String, lang: String) = Unit
        override fun isAvailable(lang: String) = false
    }
}

/**
 * Maps our content lang tags (BCP-47, script-qualified — spec 03) to the concrete
 * locale tag a TTS engine wants. Pure and testable; platform impls apply it.
 */
fun ttsLocaleTag(lang: String): String = when (lang.lowercase().substringBefore("-")) {
    "zh" -> if (lang.contains("Hant", ignoreCase = true)) "zh-TW" else "zh-CN"
    "ja" -> "ja-JP"
    "ko" -> "ko-KR"
    else -> lang
}
