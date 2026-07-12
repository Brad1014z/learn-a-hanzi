package io.github.brad1014z.hanzi.engine.svg

import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.geometry.Polyline

/**
 * Vendored pure-Kotlin SVG path parser (spec 01/05): androidx's PathParser is a
 * restricted API, and the core must stay free of android.* imports. Supports the
 * commands make-me-a-hanzi/hanzi-writer data uses (M/L/Q/C/Z) plus H/V and relative
 * forms for safety. Anything else (arcs, smooth curves) throws loudly — schema drift
 * should fail, not silently corrupt geometry (spec 02).
 */
sealed interface SvgCommand {
    data class MoveTo(val p: Point) : SvgCommand
    data class LineTo(val p: Point) : SvgCommand
    data class QuadTo(val control: Point, val p: Point) : SvgCommand
    data class CubicTo(val control1: Point, val control2: Point, val p: Point) : SvgCommand
    data object Close : SvgCommand
}

object SvgPathParser {

    private val NUMBER = Regex("""[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?""")
    private val COMMAND = Regex("""[MmLlQqCcZzHhVv]""")

    fun parse(pathData: String): List<SvgCommand> {
        val commands = mutableListOf<SvgCommand>()
        var current = Point(0.0, 0.0)
        var subpathStart = Point(0.0, 0.0)

        var i = 0
        while (i < pathData.length) {
            val ch = pathData[i]
            if (ch.isWhitespace() || ch == ',') {
                i++
                continue
            }
            if (!COMMAND.matches(ch.toString())) {
                error("Unsupported SVG path command '$ch' at index $i in: $pathData")
            }
            i++
            // Consume all numbers up to the next command letter.
            val numsEnd = pathData.drop(i).indexOfFirst { it.isLetter() }
                .let { if (it == -1) pathData.length else i + it }
            val nums = NUMBER.findAll(pathData.substring(i, numsEnd))
                .map { it.value.toDouble() }.toList()
            i = numsEnd

            val relative = ch.isLowerCase()
            fun abs(x: Double, y: Double) =
                if (relative) Point(current.x + x, current.y + y) else Point(x, y)

            when (ch.lowercaseChar()) {
                'm' -> {
                    require(nums.size >= 2 && nums.size % 2 == 0) { "MoveTo needs coordinate pairs: $pathData" }
                    var p = abs(nums[0], nums[1])
                    commands.add(SvgCommand.MoveTo(p))
                    current = p
                    subpathStart = p
                    // Extra pairs after a MoveTo are implicit LineTos (SVG spec).
                    for (k in 2 until nums.size step 2) {
                        p = abs(nums[k], nums[k + 1])
                        commands.add(SvgCommand.LineTo(p))
                        current = p
                    }
                }
                'l' -> {
                    require(nums.size >= 2 && nums.size % 2 == 0) { "LineTo needs coordinate pairs: $pathData" }
                    for (k in nums.indices step 2) {
                        val p = abs(nums[k], nums[k + 1])
                        commands.add(SvgCommand.LineTo(p))
                        current = p
                    }
                }
                'h' -> {
                    require(nums.isNotEmpty()) { "H needs at least one x: $pathData" }
                    for (x in nums) {
                        val p = if (relative) Point(current.x + x, current.y) else Point(x, current.y)
                        commands.add(SvgCommand.LineTo(p))
                        current = p
                    }
                }
                'v' -> {
                    require(nums.isNotEmpty()) { "V needs at least one y: $pathData" }
                    for (y in nums) {
                        val p = if (relative) Point(current.x, current.y + y) else Point(current.x, y)
                        commands.add(SvgCommand.LineTo(p))
                        current = p
                    }
                }
                'q' -> {
                    require(nums.size >= 4 && nums.size % 4 == 0) { "QuadTo needs 4 numbers per segment: $pathData" }
                    for (k in nums.indices step 4) {
                        val c = abs(nums[k], nums[k + 1])
                        val p = abs(nums[k + 2], nums[k + 3])
                        commands.add(SvgCommand.QuadTo(c, p))
                        current = p
                    }
                }
                'c' -> {
                    require(nums.size >= 6 && nums.size % 6 == 0) { "CubicTo needs 6 numbers per segment: $pathData" }
                    for (k in nums.indices step 6) {
                        val c1 = abs(nums[k], nums[k + 1])
                        val c2 = abs(nums[k + 2], nums[k + 3])
                        val p = abs(nums[k + 4], nums[k + 5])
                        commands.add(SvgCommand.CubicTo(c1, c2, p))
                        current = p
                    }
                }
                'z' -> {
                    commands.add(SvgCommand.Close)
                    current = subpathStart
                }
            }
        }
        return commands
    }
}

/**
 * Serialize commands back to an SVG path string (used by the ingest tool to store
 * *normalized* geometry — spec 02 step 3; the app then renders without re-transforming).
 * Coordinates are rounded to 0.1 unit (sub-pixel in the 1000-box) to keep the DB compact.
 */
fun List<SvgCommand>.toPathString(): String {
    fun n(v: Double): String {
        val r = kotlin.math.round(v * 10) / 10
        return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
    }
    fun p(pt: Point) = "${n(pt.x)} ${n(pt.y)}"
    return joinToString(" ") { cmd ->
        when (cmd) {
            is SvgCommand.MoveTo -> "M ${p(cmd.p)}"
            is SvgCommand.LineTo -> "L ${p(cmd.p)}"
            is SvgCommand.QuadTo -> "Q ${p(cmd.control)} ${p(cmd.p)}"
            is SvgCommand.CubicTo -> "C ${p(cmd.control1)} ${p(cmd.control2)} ${p(cmd.p)}"
            SvgCommand.Close -> "Z"
        }
    }
}

/** Apply an affine-ish point transform to every coordinate (used for Y-flip normalization). */
fun List<SvgCommand>.mapPoints(f: (Point) -> Point): List<SvgCommand> = map { cmd ->
    when (cmd) {
        is SvgCommand.MoveTo -> SvgCommand.MoveTo(f(cmd.p))
        is SvgCommand.LineTo -> SvgCommand.LineTo(f(cmd.p))
        is SvgCommand.QuadTo -> SvgCommand.QuadTo(f(cmd.control), f(cmd.p))
        is SvgCommand.CubicTo -> SvgCommand.CubicTo(f(cmd.control1), f(cmd.control2), f(cmd.p))
        SvgCommand.Close -> SvgCommand.Close
    }
}

/** Flatten to a polyline (curves sampled at [segmentsPerCurve]); used by tests and bounds checks. */
fun List<SvgCommand>.flatten(segmentsPerCurve: Int = 8): Polyline {
    val points = mutableListOf<Point>()
    var current = Point(0.0, 0.0)
    var subpathStart = Point(0.0, 0.0)
    for (cmd in this) {
        when (cmd) {
            is SvgCommand.MoveTo -> {
                current = cmd.p
                subpathStart = cmd.p
                points.add(cmd.p)
            }
            is SvgCommand.LineTo -> {
                current = cmd.p
                points.add(cmd.p)
            }
            is SvgCommand.QuadTo -> {
                for (s in 1..segmentsPerCurve) {
                    val t = s.toDouble() / segmentsPerCurve
                    val mt = 1 - t
                    points.add(
                        current * (mt * mt) + cmd.control * (2 * mt * t) + cmd.p * (t * t)
                    )
                }
                current = cmd.p
            }
            is SvgCommand.CubicTo -> {
                for (s in 1..segmentsPerCurve) {
                    val t = s.toDouble() / segmentsPerCurve
                    val mt = 1 - t
                    points.add(
                        current * (mt * mt * mt) +
                            cmd.control1 * (3 * mt * mt * t) +
                            cmd.control2 * (3 * mt * t * t) +
                            cmd.p * (t * t * t)
                    )
                }
                current = cmd.p
            }
            SvgCommand.Close -> {
                points.add(subpathStart)
                current = subpathStart
            }
        }
    }
    return points
}
