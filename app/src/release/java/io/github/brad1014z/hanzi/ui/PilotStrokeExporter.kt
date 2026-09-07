package io.github.brad1014z.hanzi.ui

import android.content.Context

/** Release stub: raw stroke export code is absent from production builds. */
class PilotStrokeExporter(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") consentGranted: Boolean,
    @Suppress("UNUSED_PARAMETER") facilitatorLabel: String,
) : StrokeAttemptObserver {
    override fun record(event: StrokeAttemptEvent) = Unit
}
