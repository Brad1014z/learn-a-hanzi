package io.github.brad1014z.hanzi.ui

import android.content.Context
import android.media.MediaPlayer
import io.github.brad1014z.hanzi.engine.speech.SpeechService
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Pre-generated audio player (spec 01/02, M2): plays cloud-TTS clips bundled under
 * assets/audio/<lang>/<sha1>.mp3, falling back to the device-TTS [fallback] for any
 * text without a clip. When no clips are bundled at all (audio generation is a
 * separate, key-requiring step — see data-ingest/README.md) this is a transparent
 * pass-through, so the app behaves exactly as before.
 */
class PregenAudioSpeechService(
    private val context: Context,
    private val fallback: AndroidSpeechService,
) : SpeechService {

    // manifest.json maps sha1 → text (for auditing); we only need the key set.
    private val clips: Map<String, Set<String>> = buildMap {
        for (lang in listOf("zh-Hans")) {
            val json = runCatching {
                context.assets.open("audio/$lang/manifest.json").bufferedReader().readText()
            }.getOrNull() ?: continue
            put(lang, JSONObject(json).keys().asSequence().toSet())
        }
    }
    private var player: MediaPlayer? = null

    override fun speak(text: String, lang: String) {
        val key = sha1(text)
        if (clips[lang]?.contains(key) == true) {
            // A new request replaces the current clip (spec 07: debounced).
            player?.release()
            player = MediaPlayer().apply {
                context.assets.openFd("audio/$lang/$key.mp3").use { fd ->
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare()
                start()
            }
        } else {
            fallback.speak(text, lang)
        }
    }

    override fun isAvailable(lang: String): Boolean =
        clips[lang]?.isNotEmpty() == true || fallback.isAvailable(lang)

    fun release() {
        player?.release()
        player = null
        fallback.release()
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
