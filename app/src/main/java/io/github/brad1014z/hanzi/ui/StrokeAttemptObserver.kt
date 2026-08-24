package io.github.brad1014z.hanzi.ui

import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict

data class TimedStrokePoint(val point: Point, val relativeMillis: Long)

data class StrokeAttemptEvent(
    val character: String,
    val expectedStrokeIndex: Int,
    val points: List<TimedStrokePoint>,
    val verdict: StrokeVerdict,
    val retryCount: Int,
)

fun interface StrokeAttemptObserver {
    fun record(event: StrokeAttemptEvent)

    data object None : StrokeAttemptObserver {
        override fun record(event: StrokeAttemptEvent) = Unit
    }
}
