package io.github.brad1014z.hanzi.ui

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import io.github.brad1014z.hanzi.BuildConfig
import io.github.brad1014z.hanzi.engine.grading.StrokeScores
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Debug-only, consent-gated JSONL writer. It has no network or upload path. */
class PilotStrokeExporter(
    context: Context,
    private val consentGranted: Boolean,
    private val facilitatorLabel: String,
) : StrokeAttemptObserver {
    private val appContext = context.applicationContext
    private val sessionId = appContext.getSharedPreferences("pilot-export", Context.MODE_PRIVATE)
        .let { preferences ->
            preferences.getString("sessionId", null) ?: UUID.randomUUID().toString().also {
                preferences.edit { putString("sessionId", it) }
            }
        }

    override fun record(event: StrokeAttemptEvent) {
        if (!BuildConfig.DEBUG || !consentGranted) return
        val scores = event.verdict.scoresOrNull()
        val payload = buildJsonObject {
            put("sessionId", sessionId)
            put("appBuild", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("deviceProfile", "${Build.MANUFACTURER} ${Build.MODEL}; sdk ${Build.VERSION.SDK_INT}")
            put("character", event.character)
            put("expectedStrokeIndex", event.expectedStrokeIndex)
            put("retryCount", event.retryCount)
            put("facilitatorLabel", facilitatorLabel)
            put("result", event.verdict.resultName())
            scores?.let {
                put("meanDistance", it.meanDist)
                put("directionScore", it.directionScore)
                put("lengthRatio", it.lengthRatio)
            }
            put("points", buildJsonArray {
                event.points.forEach { timed ->
                    add(buildJsonObject {
                        put("x", timed.point.x)
                        put("y", timed.point.y)
                        put("tMs", timed.relativeMillis)
                    })
                }
            })
        }
        val directory = File(appContext.filesDir, "pilot-strokes").apply { mkdirs() }
        File(directory, "$sessionId.jsonl").appendText(Json.encodeToString(payload) + "\n")
    }
}

private fun StrokeVerdict.scoresOrNull(): StrokeScores? = when (this) {
    is StrokeVerdict.Accept -> scores
    is StrokeVerdict.WrongOrder -> scores
    is StrokeVerdict.Reject -> scores
    StrokeVerdict.Ignored -> null
}

private fun StrokeVerdict.resultName(): String = when (this) {
    is StrokeVerdict.Accept -> if (sloppy) "accepted-sloppy" else "accepted"
    is StrokeVerdict.WrongOrder -> "wrong-order"
    is StrokeVerdict.Reject -> "rejected-${reason.name.lowercase()}"
    StrokeVerdict.Ignored -> "ignored"
}
