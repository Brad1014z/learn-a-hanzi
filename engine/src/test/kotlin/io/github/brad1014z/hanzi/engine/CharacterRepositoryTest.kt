package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.data.CharacterRepository
import io.github.brad1014z.hanzi.engine.svg.flatten
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterRepositoryTest {

    private val repo = CharacterRepository()

    /** Ground truth: real stroke counts for the Phase 0 slice. */
    private val expectedStrokeCounts = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "十" to 2, "人" to 2,
        "大" to 3, "小" to 3, "上" to 3, "口" to 3, "中" to 4,
        "日" to 4, "月" to 4, "山" to 3, "水" to 4, "火" to 4,
        "木" to 4, "心" to 4, "我" to 7, "你" to 7, "好" to 6,
    )

    @Test
    fun `index lists all 20 prototype characters`() {
        val chars = repo.listCharacters()
        assertEquals(20, chars.size)
        assertEquals(expectedStrokeCounts.keys, chars.toSet())
    }

    @Test
    fun `every character loads with the correct stroke count and parallel medians`() {
        for ((char, expected) in expectedStrokeCounts) {
            val data = repo.load(char)
            assertEquals(expected, data.strokeCount, "$char stroke count")
            assertEquals(data.strokeOutlines.size, data.medians.size, "$char parallel arrays")
        }
    }

    @Test
    fun `normalized geometry stays within the 1000-space box`() {
        // Small tolerance: some glyphs overshoot the em-square slightly.
        val range = -60.0..1060.0
        for (char in repo.listCharacters()) {
            val data = repo.load(char)
            for (median in data.medians) {
                assertTrue(median.all { it.x in range && it.y in range }, "$char median out of bounds")
            }
            for (outline in data.strokeOutlines) {
                assertTrue(
                    outline.flatten().all { it.x in range && it.y in range },
                    "$char outline out of bounds",
                )
            }
        }
    }

    @Test
    fun `y-flip is applied - three's strokes run top to bottom in screen coordinates`() {
        // 三 is written top stroke first, bottom stroke last. In Y-down screen space the
        // first median must sit ABOVE (smaller y) the last. If the Y-flip were missing,
        // this inverts — the classic upside-down-characters bug (specs 02/05).
        val three = repo.load("三")
        val firstY = three.medians.first().map { it.y }.average()
        val lastY = three.medians.last().map { it.y }.average()
        assertTrue(firstY < lastY, "三: first stroke (y=$firstY) should be above last (y=$lastY)")
    }
}
