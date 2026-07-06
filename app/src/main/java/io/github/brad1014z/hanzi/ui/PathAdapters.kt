package io.github.brad1014z.hanzi.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.geometry.Polyline
import io.github.brad1014z.hanzi.engine.geometry.arcLength
import io.github.brad1014z.hanzi.engine.svg.SvgCommand

/**
 * Feature-layer adapters (spec 06): the engine speaks pure geometry; only here does it
 * become Compose types. The engine module never imports android.* — these do.
 */

/** Maps between the engine's 1000×1000 space and canvas pixels (centered square). */
class CanvasMapping(canvasWidth: Float, canvasHeight: Float) {
    val side: Float = minOf(canvasWidth, canvasHeight)
    private val ox: Float = (canvasWidth - side) / 2f
    private val oy: Float = (canvasHeight - side) / 2f
    private val k: Float = side / 1000f

    fun toCanvas(p: Point) = Offset(ox + p.x.toFloat() * k, oy + p.y.toFloat() * k)
    fun toEngine(o: Offset) = Point(((o.x - ox) / k).toDouble(), ((o.y - oy) / k).toDouble())
    /** Scale a stroke width from 1000-space units to pixels. */
    fun widthPx(units: Float): Float = units * k
}

fun List<SvgCommand>.toComposePath(mapping: CanvasMapping): Path {
    val path = Path()
    for (cmd in this) {
        when (cmd) {
            is SvgCommand.MoveTo -> mapping.toCanvas(cmd.p).let { path.moveTo(it.x, it.y) }
            is SvgCommand.LineTo -> mapping.toCanvas(cmd.p).let { path.lineTo(it.x, it.y) }
            is SvgCommand.QuadTo -> {
                val c = mapping.toCanvas(cmd.control)
                val p = mapping.toCanvas(cmd.p)
                path.quadraticTo(c.x, c.y, p.x, p.y)
            }
            is SvgCommand.CubicTo -> {
                val c1 = mapping.toCanvas(cmd.control1)
                val c2 = mapping.toCanvas(cmd.control2)
                val p = mapping.toCanvas(cmd.p)
                path.cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
            }
            SvgCommand.Close -> path.close()
        }
    }
    return path
}

fun Polyline.toLinePath(mapping: CanvasMapping): Path {
    val path = Path()
    if (isEmpty()) return path
    val first = mapping.toCanvas(this[0])
    path.moveTo(first.x, first.y)
    for (i in 1 until size) {
        val p = mapping.toCanvas(this[i])
        path.lineTo(p.x, p.y)
    }
    return path
}

/** Prefix of a polyline up to arc-length fraction [t] — drives the demo's growing stroke. */
fun Polyline.trimToFraction(t: Double): Polyline {
    if (size < 2 || t >= 1.0) return this
    if (t <= 0.0) return listOf(first())
    val target = arcLength() * t
    val result = mutableListOf(first())
    var travelled = 0.0
    for (i in 1 until size) {
        val a = this[i - 1]
        val b = this[i]
        val seg = a.distanceTo(b)
        if (travelled + seg >= target) {
            if (seg > 1e-9) {
                val u = (target - travelled) / seg
                result.add(a + (b - a) * u)
            }
            return result
        }
        travelled += seg
        result.add(b)
    }
    return result
}
