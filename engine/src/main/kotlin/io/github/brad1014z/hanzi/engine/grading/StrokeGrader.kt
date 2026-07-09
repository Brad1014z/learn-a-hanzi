package io.github.brad1014z.hanzi.engine.grading

import io.github.brad1014z.hanzi.engine.geometry.Polyline
import io.github.brad1014z.hanzi.engine.geometry.arcLength
import io.github.brad1014z.hanzi.engine.geometry.directionScore
import io.github.brad1014z.hanzi.engine.geometry.endpointDirectionScore
import io.github.brad1014z.hanzi.engine.geometry.resample
import io.github.brad1014z.hanzi.engine.geometry.simplifyRdp
import io.github.brad1014z.hanzi.engine.geometry.symmetricMeanDistance
import kotlin.math.min

/**
 * All grading thresholds in one tunable place (spec 05). Values are in 1000-space units;
 * defaults are the spec's starting points, tuned on-device during Phase 0 — this is a
 * data class (not constants) so the tuning screen can be "sliders for feelings" (spec 11, S4).
 */
data class GradingConfig(
    val rdpEpsilon: Double = 2.0,
    val resampleStep: Double = 10.0,
    val acceptDist: Double = 45.0,
    val acceptDir: Double = 0.6,
    val sloppyDist: Double = 70.0,
    val sloppyDir: Double = 0.4,
    val maxSloppyBeforeFail: Int = 2,
    val lookaheadStrokes: Int = 2,
    val lenRatioMin: Double = 0.5,
    val lenRatioMax: Double = 2.0,
    val shortStrokeLen: Double = 60.0,
    val minStrokeUnits: Double = 15.0,
    /** After a hint, the next attempt is accepted more leniently (spec 05, failure UX). */
    val hintDistLeniency: Double = 1.3,
    val hintDirLeniency: Double = 0.75,
) {
    companion object {
        val Default = GradingConfig()
    }
}

data class StrokeScores(
    val meanDist: Double,
    val directionScore: Double,
    val lengthRatio: Double,
)

enum class RejectReason { LENGTH_OUT_OF_RANGE, WRONG_DIRECTION, TOO_FAR }

sealed interface StrokeVerdict {
    /** Right stroke, right place — advance. [sloppy] marks the "close enough" tier. */
    data class Accept(val sloppy: Boolean, val scores: StrokeScores) : StrokeVerdict

    /** Right kind of stroke, wrong order: [matchedIndex] is the stroke it fits better. */
    data class WrongOrder(val matchedIndex: Int, val scores: StrokeScores) : StrokeVerdict

    data class Reject(val reason: RejectReason, val scores: StrokeScores?) : StrokeVerdict

    /** Accidental contact (palm graze / stray tap) — no feedback, not an attempt. */
    data object Ignored : StrokeVerdict
}

/**
 * Grades one user stroke against the expected stroke's median (spec 05, quiz mode).
 * Pure and stateless: (input, expected index, medians) → verdict. The time component of
 * accidental-contact filtering (<~30ms) is the UI layer's job; the length floor is here.
 */
class StrokeGrader(private val config: GradingConfig = GradingConfig.Default) {

    fun grade(
        rawStroke: Polyline,
        expectedIndex: Int,
        medians: List<Polyline>,
        hintActive: Boolean = false,
    ): StrokeVerdict {
        require(expectedIndex in medians.indices) { "expectedIndex $expectedIndex out of range" }

        // Step 1 — capture & clean (spec 05).
        if (rawStroke.size < 2 || rawStroke.arcLength() < config.minStrokeUnits) {
            return StrokeVerdict.Ignored
        }
        val user = rawStroke.simplifyRdp(config.rdpEpsilon).resample(config.resampleStep)

        // Step 2 — match against the expected stroke.
        val distCap = if (hintActive) config.hintDistLeniency else 1.0
        val dirFloor = if (hintActive) config.hintDirLeniency else 1.0
        val expectedScores = score(user, medians[expectedIndex])
        val tier = classify(expectedScores, distCap, dirFloor)
        if (tier != null) {
            return StrokeVerdict.Accept(sloppy = tier == Tier.SLOPPY, scores = expectedScores)
        }

        // Step 3 — would a nearby future stroke have matched better? (out-of-order feedback)
        val lastLookahead = min(expectedIndex + config.lookaheadStrokes, medians.lastIndex)
        for (i in (expectedIndex + 1)..lastLookahead) {
            val s = score(user, medians[i])
            if (classify(s, distCap, dirFloor) != null) {
                return StrokeVerdict.WrongOrder(matchedIndex = i, scores = s)
            }
        }

        val reason = when {
            expectedScores.lengthRatio !in config.lenRatioMin..config.lenRatioMax ->
                RejectReason.LENGTH_OUT_OF_RANGE
            expectedScores.meanDist <= config.sloppyDist * distCap ->
                RejectReason.WRONG_DIRECTION
            else -> RejectReason.TOO_FAR
        }
        return StrokeVerdict.Reject(reason, expectedScores)
    }

    private enum class Tier { CLEAN, SLOPPY }

    private fun score(user: Polyline, expected: Polyline): StrokeScores {
        val lengthRatio = user.arcLength() / expected.arcLength().coerceAtLeast(1e-9)
        val meanDist = symmetricMeanDistance(user, expected)
        // Dot strokes (spec 05): tangent sampling is noise on a 2–3 point polyline —
        // grade position + a single endpoint vector instead.
        val dir = if (expected.arcLength() < config.shortStrokeLen) {
            endpointDirectionScore(user, expected)
        } else {
            directionScore(user, expected)
        }
        return StrokeScores(meanDist = meanDist, directionScore = dir, lengthRatio = lengthRatio)
    }

    private fun classify(s: StrokeScores, distCap: Double, dirFloor: Double): Tier? {
        // Length guard runs before scoring tiers (catches taps and median-hugging scribbles).
        if (s.lengthRatio !in config.lenRatioMin..config.lenRatioMax) return null
        return when {
            s.meanDist <= config.acceptDist * distCap &&
                s.directionScore >= config.acceptDir * dirFloor -> Tier.CLEAN
            s.meanDist <= config.sloppyDist * distCap &&
                s.directionScore >= config.sloppyDir * dirFloor -> Tier.SLOPPY
            else -> null
        }
    }
}
