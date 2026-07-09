package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.data.CharacterRepository
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.geometry.Polyline
import io.github.brad1014z.hanzi.engine.geometry.arcLength
import io.github.brad1014z.hanzi.engine.geometry.resample
import io.github.brad1014z.hanzi.engine.grading.GradingConfig
import io.github.brad1014z.hanzi.engine.grading.RejectReason
import io.github.brad1014z.hanzi.engine.grading.StrokeGrader
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The golden corpus, v0 (spec 05 / roadmap Phase 0): synthetic attempts derived from the
 * real medians of the checked-in characters — clean, jittered, reversed, out-of-order,
 * displaced, truncated. Recorded real-finger strokes join this corpus during on-device
 * tuning sessions; the replay mechanism is the same (polyline in → verdict asserted).
 */
class StrokeGraderTest {

    private val repo = CharacterRepository()
    private val grader = StrokeGrader()
    private val config = GradingConfig.Default

    private fun jitter(stroke: Polyline, magnitude: Double, seed: Int): Polyline {
        val rng = Random(seed)
        return stroke.map {
            Point(
                it.x + rng.nextDouble(-magnitude, magnitude),
                it.y + rng.nextDouble(-magnitude, magnitude),
            )
        }
    }

    @Test
    fun `clean strokes - the exact median is always a clean accept`() {
        for (char in repo.listCharacters()) {
            val data = repo.load(char)
            for ((i, median) in data.medians.withIndex()) {
                val verdict = grader.grade(median, i, data.medians)
                assertIs<StrokeVerdict.Accept>(verdict, "$char stroke $i")
                assertTrue(!verdict.sloppy, "$char stroke $i should be clean, was sloppy: ${verdict.scores}")
            }
        }
    }

    @Test
    fun `honest wobble - lightly jittered strokes are accepted`() {
        for (char in listOf("一", "十", "火", "心", "我")) {
            val data = repo.load(char)
            for ((i, median) in data.medians.withIndex()) {
                val attempt = jitter(median.resample(config.resampleStep), 10.0, seed = i + char.hashCode())
                val verdict = grader.grade(attempt, i, data.medians)
                assertIs<StrokeVerdict.Accept>(verdict, "$char stroke $i with 10u jitter: $verdict")
            }
        }
    }

    @Test
    fun `backwards strokes are rejected for wrong direction`() {
        // Long strokes only — direction is meaningless noise on dots.
        val one = repo.load("一")
        val verdict = grader.grade(one.medians[0].reversed(), 0, one.medians)
        assertIs<StrokeVerdict.Reject>(verdict)
        assertEquals(RejectReason.WRONG_DIRECTION, verdict.reason)
    }

    @Test
    fun `right stroke at the wrong time is called out of order`() {
        // 十: horizontal first, vertical second. Drawing the vertical first should be
        // flagged as out-of-order (it matches stroke 1), not generically rejected.
        val shi = repo.load("十")
        val verdict = grader.grade(shi.medians[1], 0, shi.medians)
        assertIs<StrokeVerdict.WrongOrder>(verdict)
        assertEquals(1, verdict.matchedIndex)
    }

    @Test
    fun `a stroke drawn far from its place is rejected`() {
        val one = repo.load("一")
        val displaced = one.medians[0].map { Point(it.x + 250.0, it.y + 250.0) }
        val verdict = grader.grade(displaced, 0, one.medians)
        assertIs<StrokeVerdict.Reject>(verdict)
        assertEquals(RejectReason.TOO_FAR, verdict.reason)
    }

    @Test
    fun `a truncated stroke fails the length guard`() {
        val one = repo.load("一")
        val resampled = one.medians[0].resample(config.resampleStep)
        val stub = resampled.take((resampled.size * 0.3).toInt().coerceAtLeast(2))
        val verdict = grader.grade(stub, 0, one.medians)
        assertIs<StrokeVerdict.Reject>(verdict)
        assertEquals(RejectReason.LENGTH_OUT_OF_RANGE, verdict.reason)
    }

    @Test
    fun `dot strokes use the endpoint-vector path`() {
        // Synthetic dot: expected arc length below shortStrokeLen (real 点 medians are
        // often longer, so the code path is pinned with a synthetic one).
        val dot = listOf(Point(500.0, 500.0), Point(530.0, 540.0))
        assertTrue(dot.arcLength() < config.shortStrokeLen)
        val medians = listOf(dot)

        val clean = grader.grade(jitter(dot, 8.0, seed = 1), 0, medians)
        assertIs<StrokeVerdict.Accept>(clean, "clean dot: $clean")

        val backwards = grader.grade(dot.reversed(), 0, medians)
        assertIs<StrokeVerdict.Reject>(backwards, "backwards dot: $backwards")
    }

    @Test
    fun `accidental contacts are ignored, not rejected`() {
        val one = repo.load("一")
        val graze = listOf(Point(500.0, 500.0), Point(505.0, 503.0)) // ~6 units
        assertEquals(StrokeVerdict.Ignored, grader.grade(graze, 0, one.medians))
    }

    @Test
    fun `hint leniency accepts an attempt that would otherwise be rejected`() {
        val one = repo.load("一")
        // Offset chosen between sloppyDist (70) and hint-lenient cap (70 × 1.3 = 91).
        val rough = one.medians[0].map { Point(it.x, it.y + 80.0) }
        val without = grader.grade(rough, 0, one.medians, hintActive = false)
        val with = grader.grade(rough, 0, one.medians, hintActive = true)
        assertIs<StrokeVerdict.Reject>(without)
        assertIs<StrokeVerdict.Accept>(with)
    }
}
