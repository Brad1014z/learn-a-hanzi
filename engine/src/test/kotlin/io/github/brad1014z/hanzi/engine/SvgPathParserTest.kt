package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.svg.SvgCommand
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import io.github.brad1014z.hanzi.engine.svg.flatten
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SvgPathParserTest {

    @Test
    fun `parses the command set hanzi data uses`() {
        val commands = SvgPathParser.parse("M 320 717 Q 320 700 310 690 L 300 680 C 290 670 280 660 270 650 Z")
        assertEquals(
            listOf(
                SvgCommand.MoveTo(Point(320.0, 717.0)),
                SvgCommand.QuadTo(Point(320.0, 700.0), Point(310.0, 690.0)),
                SvgCommand.LineTo(Point(300.0, 680.0)),
                SvgCommand.CubicTo(Point(290.0, 670.0), Point(280.0, 660.0), Point(270.0, 650.0)),
                SvgCommand.Close,
            ),
            commands,
        )
    }

    @Test
    fun `handles relative commands and implicit repeats`() {
        val commands = SvgPathParser.parse("m 10 10 l 5 0 5 0 z")
        assertEquals(
            listOf(
                SvgCommand.MoveTo(Point(10.0, 10.0)),
                SvgCommand.LineTo(Point(15.0, 10.0)),
                SvgCommand.LineTo(Point(20.0, 10.0)),
                SvgCommand.Close,
            ),
            commands,
        )
    }

    @Test
    fun `handles negative and decimal numbers without separators`() {
        val commands = SvgPathParser.parse("M-10.5-20.25L.5.75")
        assertEquals(
            listOf(
                SvgCommand.MoveTo(Point(-10.5, -20.25)),
                SvgCommand.LineTo(Point(0.5, 0.75)),
            ),
            commands,
        )
    }

    @Test
    fun `fails loudly on unsupported commands`() {
        assertFailsWith<IllegalStateException> {
            SvgPathParser.parse("M 0 0 A 5 5 0 0 1 10 10")
        }
    }

    @Test
    fun `flatten samples curves into a bounded polyline`() {
        val poly = SvgPathParser.parse("M 0 0 Q 50 100 100 0").flatten(segmentsPerCurve = 10)
        assertEquals(11, poly.size)
        assertTrue(poly.all { it.y in 0.0..50.0 + 1e-9 }, "quad peak is at control/2")
        assertEquals(Point(100.0, 0.0), poly.last())
    }
}
