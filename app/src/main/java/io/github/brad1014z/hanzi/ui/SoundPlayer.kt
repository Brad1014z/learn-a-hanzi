package io.github.brad1014z.hanzi.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.brad1014z.hanzi.R

/**
 * Grading sound effects (spec 07: audio feedback on verdicts, toggleable).
 * The WAVs are synthesized placeholders — TODO(son, S4): verdict feedback is your
 * design; replace these with sounds that feel right (res/raw, then re-map here).
 */
class SoundPlayer(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val correct = pool.load(context, R.raw.stroke_correct, 1)
    private val wrong = pool.load(context, R.raw.stroke_wrong, 1)
    private val complete = pool.load(context, R.raw.char_complete, 1)

    var enabled: Boolean = true

    fun playCorrect() = play(correct)
    fun playWrong() = play(wrong, volume = 0.7f) // rejection stays gentle (spec 00)
    fun playComplete() = play(complete)

    private fun play(soundId: Int, volume: Float = 1f) {
        if (enabled) pool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}
