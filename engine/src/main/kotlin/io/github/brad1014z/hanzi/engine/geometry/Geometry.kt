package io.github.brad1014z.hanzi.engine.geometry

import kotlin.math.sqrt

data class Point(val x: Double, val y: Double) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(k: Double) = Point(x * k, y * k)
    fun dot(other: Point) = x * other.x + y * other.y
    fun length() = sqrt(x * x + y * y)
    fun distanceTo(other: Point) = (this - other).length()
    fun normalized(): Point {
        val len = length()
        return if (len < 1e-9) Point(0.0, 0.0) else Point(x / len, y / len)
    }
}

typealias Polyline = List<Point>

fun Polyline.arcLength(): Double {
    var total = 0.0
    for (i in 1 until size) total += this[i - 1].distanceTo(this[i])
    return total
}

/**
 * Resample to a fixed point density (one point every [step] units of arc length),
 * always keeping the first and last points, so comparisons aren't biased by drawing
 * speed or raw point count (spec 05, step 1).
 */
fun Polyline.resample(step: Double): Polyline {
    if (size < 2) return this
    val total = arcLength()
    if (total < 1e-9) return listOf(first())
    val result = mutableListOf(first())
    var nextAt = step
    var travelled = 0.0
    for (i in 1 until size) {
        val a = this[i - 1]
        val b = this[i]
        val segLen = a.distanceTo(b)
        if (segLen < 1e-9) continue
        while (nextAt <= travelled + segLen) {
            val t = (nextAt - travelled) / segLen
            result.add(a + (b - a) * t)
            nextAt += step
        }
        travelled += segLen
    }
    if (result.last().distanceTo(last()) > 1e-9) result.add(last())
    return result
}

/** Ramer–Douglas–Peucker simplification (spec 05, step 1). */
fun Polyline.simplifyRdp(epsilon: Double): Polyline {
    if (size < 3) return this
    var maxDist = 0.0
    var index = 0
    for (i in 1 until size - 1) {
        val d = pointSegmentDistance(this[i], first(), last())
        if (d > maxDist) {
            maxDist = d
            index = i
        }
    }
    return if (maxDist > epsilon) {
        val left = subList(0, index + 1).simplifyRdp(epsilon)
        val right = subList(index, size).simplifyRdp(epsilon)
        left.dropLast(1) + right
    } else {
        listOf(first(), last())
    }
}

fun pointSegmentDistance(p: Point, a: Point, b: Point): Double {
    val ab = b - a
    val lenSq = ab.dot(ab)
    if (lenSq < 1e-12) return p.distanceTo(a)
    val t = ((p - a).dot(ab) / lenSq).coerceIn(0.0, 1.0)
    return p.distanceTo(a + ab * t)
}

fun Polyline.minDistanceTo(p: Point): Double {
    if (isEmpty()) return Double.MAX_VALUE
    if (size == 1) return p.distanceTo(first())
    var best = Double.MAX_VALUE
    for (i in 1 until size) {
        val d = pointSegmentDistance(p, this[i - 1], this[i])
        if (d < best) best = d
    }
    return best
}

/**
 * Chamfer-like symmetric mean distance between two polylines (spec 05, position score):
 * mean of every point's distance to the other polyline, in both directions.
 */
fun symmetricMeanDistance(a: Polyline, b: Polyline): Double {
    if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE
    var sum = 0.0
    for (p in a) sum += b.minDistanceTo(p)
    for (p in b) sum += a.minDistanceTo(p)
    return sum / (a.size + b.size)
}

/** Point at arc-length fraction [t] in 0..1 along the polyline. */
fun Polyline.pointAtFraction(t: Double): Point {
    if (isEmpty()) throw IllegalArgumentException("empty polyline")
    if (size == 1) return first()
    val target = arcLength() * t.coerceIn(0.0, 1.0)
    var travelled = 0.0
    for (i in 1 until size) {
        val a = this[i - 1]
        val b = this[i]
        val segLen = a.distanceTo(b)
        if (travelled + segLen >= target && segLen > 1e-9) {
            val u = (target - travelled) / segLen
            return a + (b - a) * u
        }
        travelled += segLen
    }
    return last()
}

/** Unit tangent at arc-length fraction [t], via a small central difference. */
fun Polyline.tangentAtFraction(t: Double, dt: Double = 0.05): Point {
    val t0 = (t - dt).coerceIn(0.0, 1.0)
    val t1 = (t + dt).coerceIn(0.0, 1.0)
    return (pointAtFraction(t1) - pointAtFraction(t0)).normalized()
}

/**
 * Direction score (spec 05, step 2b): mean cosine similarity of tangents sampled at
 * matched arc-length fractions. Rejects strokes drawn backwards or with the wrong sweep.
 */
fun directionScore(user: Polyline, expected: Polyline, samples: Int = 8): Double {
    if (user.size < 2 || expected.size < 2) return endpointDirectionScore(user, expected)
    var sum = 0.0
    for (k in 0 until samples) {
        val t = (k + 0.5) / samples
        sum += user.tangentAtFraction(t).dot(expected.tangentAtFraction(t))
    }
    return sum / samples
}

/**
 * Fallback for dot strokes (spec 05): cosine between the start→end vectors. Tangent
 * sampling is noise on a 2–3 point polyline.
 */
fun endpointDirectionScore(user: Polyline, expected: Polyline): Double {
    if (user.isEmpty() || expected.isEmpty()) return -1.0
    val u = (user.last() - user.first()).normalized()
    val e = (expected.last() - expected.first()).normalized()
    if (u.length() < 1e-9 || e.length() < 1e-9) return 1.0 // both are points: position decides
    return u.dot(e)
}
