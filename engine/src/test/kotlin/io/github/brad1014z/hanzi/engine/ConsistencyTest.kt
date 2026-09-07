package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.progress.Consistency
import io.github.brad1014z.hanzi.engine.progress.consistencyOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Consistency reporting (spec 10). The properties that matter are the *absences* of
 * things: no loss state, no decrease, no shaming — plus the one positive signal, the
 * comeback.
 */
class ConsistencyTest {

    private val today = 20_000L // arbitrary epoch day

    @Test
    fun `a brand-new learner has nothing and is not shamed for it`() {
        val c = consistencyOf(emptyList(), today)
        assertEquals(Consistency.None, c)
        assertEquals(0, c.currentRun)
        assertFalse(c.isComeback)
    }

    @Test
    fun `practising today starts a run of one`() {
        val c = consistencyOf(listOf(today), today)
        assertEquals(1, c.daysPlayed)
        assertEquals(1, c.currentRun)
        assertTrue(c.playedToday)
        assertFalse(c.isComeback, "a first day is not a comeback")
    }

    @Test
    fun `consecutive days accumulate into a run`() {
        val c = consistencyOf(listOf(today - 2, today - 1, today), today)
        assertEquals(3, c.currentRun)
        assertEquals(3, c.daysPlayed)
    }

    @Test
    fun `a run survives a today that has not been practised yet`() {
        // Practised yesterday and the day before; today is still young.
        val c = consistencyOf(listOf(today - 2, today - 1), today)
        assertFalse(c.playedToday)
        assertEquals(
            2,
            c.currentRun,
            "today isn't over — an unpractised morning must not erase a live run",
        )
    }

    @Test
    fun `a run ends only after a whole unpractised day has passed`() {
        // Last practised two days ago: yesterday went by with nothing.
        val c = consistencyOf(listOf(today - 2), today)
        assertEquals(0, c.currentRun)
        assertEquals(1, c.daysPlayed, "the day played is still counted, forever")
    }

    @Test
    fun `returning after two missed days is a comeback`() {
        // Practised, missed two full days, practised again today.
        val c = consistencyOf(listOf(today - 3, today), today)
        assertTrue(c.isComeback)
        assertEquals(2, c.missedBeforeToday)
        assertEquals(1, c.currentRun, "the new run starts at one — framed as day 1, not as a loss")
        assertEquals(2, c.daysPlayed)
    }

    @Test
    fun `missing a single day is just a day, not a comeback`() {
        val c = consistencyOf(listOf(today - 2, today), today)
        assertFalse(c.isComeback, "one missed day should not trigger a welcome-back moment")
        assertEquals(1, c.missedBeforeToday)
    }

    @Test
    fun `a long absence is still a comeback and never a negative number`() {
        val c = consistencyOf(listOf(today - 400, today), today)
        assertTrue(c.isComeback)
        assertEquals(399, c.missedBeforeToday)
        assertEquals(1, c.currentRun)
    }

    @Test
    fun `duplicate and unordered days are tolerated`() {
        // The query returns distinct days, but nothing should depend on that.
        val c = consistencyOf(listOf(today, today - 1, today, today - 2, today - 1), today)
        assertEquals(3, c.daysPlayed)
        assertEquals(3, c.currentRun)
    }

    @Test
    fun `future days do not corrupt the run or the comeback`() {
        // Defensive: a device clock moved backwards could leave "future" rows behind.
        val c = consistencyOf(listOf(today + 5, today), today)
        assertTrue(c.playedToday)
        assertEquals(1, c.currentRun)
        assertFalse(c.isComeback, "a future row is not a previous day played")
        assertEquals(0, c.missedBeforeToday)
    }
}
