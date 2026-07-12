package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.data.CharacterRepository
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import io.github.brad1014z.hanzi.engine.svg.flatten
import io.github.brad1014z.hanzi.engine.svg.toPathString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SvgPathSerializeTest {

    @Test
    fun `serialize then re-parse round-trips geometry within rounding tolerance`() {
        val repo = CharacterRepository()
        for (char in listOf("火", "我", "心")) {
            for (outline in repo.load(char).strokeOutlines) {
                val reparsed = SvgPathParser.parse(outline.toPathString())
                val a = outline.flatten()
                val b = reparsed.flatten()
                assertTrue(a.size == b.size, "$char: point count changed in round-trip")
                for (i in a.indices) {
                    assertTrue(
                        abs(a[i].x - b[i].x) <= 0.06 && abs(a[i].y - b[i].y) <= 0.06,
                        "$char: point $i drifted: ${a[i]} vs ${b[i]}",
                    )
                }
            }
        }
    }
}
