package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.geometry.arcLength
import io.github.brad1014z.hanzi.engine.geometry.directionScore
import io.github.brad1014z.hanzi.engine.geometry.resample
import io.github.brad1014z.hanzi.engine.geometry.simplifyRdp
import io.github.brad1014z.hanzi.engine.geometry.symmetricMeanDistance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {

    private fun line(vararg coords: Double): List<Point> =
        coords.toList().chunked(2).map { (x, y) -> Point(x, y) }

    @Test
    fun `arc length of a straight segment`() {
        assertEquals(100.0, line(0.0, 0.0, 100.0, 0.0).arcLength(), 1e-9)
        assertEquals(5.0, line(0.0, 0.0, 3.0, 4.0).arcLength(), 1e-9)
    }

    @Test
    fun `resample produces evenly spaced points and keeps endpoints`() {
        val resampled = line(0.0, 0.0, 100.0, 0.0).resample(10.0)
        assertEquals(11, resampled.size)
        assertEquals(Point(0.0, 0.0), resampled.first())
        assertEquals(Point(100.0, 0.0), resampled.last())
        for (i in 1 until resampled.size) {
            assertEquals(10.0, resampled[i - 1].distanceTo(resampled[i]), 1e-6)
        }
    }

    @Test
    fun `rdp drops collinear points and keeps corners`() {
        val zigzag = line(0.0, 0.0, 50.0, 0.1, 100.0, 0.0, 100.0, 100.0)
        val simplified = zigzag.simplifyRdp(2.0)
        assertEquals(listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 100.0)), simplified)
    }

    @Test
    fun `symmetric distance is zero for identical polylines and grows with offset`() {
        val a = line(0.0, 0.0, 100.0, 0.0, 200.0, 0.0)
        assertEquals(0.0, symmetricMeanDistance(a, a), 1e-9)
        val shifted = a.map { Point(it.x, it.y + 30.0) }
        assertEquals(30.0, symmetricMeanDistance(a, shifted), 1.0)
    }

    @Test
    fun `direction score is 1 for same direction and -1 for reversed`() {
        val a = line(0.0, 0.0, 100.0, 0.0).resample(10.0)
        assertEquals(1.0, directionScore(a, a), 1e-6)
        assertEquals(-1.0, directionScore(a.reversed(), a), 1e-6)
    }
}
