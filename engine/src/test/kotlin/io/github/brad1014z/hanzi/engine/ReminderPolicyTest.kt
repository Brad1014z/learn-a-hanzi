package io.github.brad1014z.hanzi.engine

import io.github.brad1014z.hanzi.engine.reminder.ReminderPolicy
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The opt-in daily reminder (spec 10). The property under test: it only ever nudges
 * someone who hasn't shown up yet, and it lands near the time they picked.
 */
class ReminderPolicyTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `never notifies when disabled, regardless of whether today was played`() {
        assertFalse(ReminderPolicy.shouldNotify(enabled = false, playedToday = false))
        assertFalse(ReminderPolicy.shouldNotify(enabled = false, playedToday = true))
    }

    @Test
    fun `never notifies a day already played -- the whole point is the nudge, not the nag`() {
        assertFalse(ReminderPolicy.shouldNotify(enabled = true, playedToday = true))
    }

    @Test
    fun `notifies only when enabled and not yet played`() {
        assertTrue(ReminderPolicy.shouldNotify(enabled = true, playedToday = false))
    }

    @Test
    fun `next trigger is later today when the chosen time has not passed`() {
        val now = Instant.parse("2026-09-07T12:00:00Z") // 08:00 America/New_York
        val next = ReminderPolicy.nextTrigger(hour = 18, minute = 0, now = now, zone = zone)
        assertEquals(Instant.parse("2026-09-07T22:00:00Z"), next) // 18:00 EDT same day
    }

    @Test
    fun `next trigger rolls to tomorrow once the chosen time has passed`() {
        val now = Instant.parse("2026-09-07T23:00:00Z") // 19:00 America/New_York
        val next = ReminderPolicy.nextTrigger(hour = 18, minute = 0, now = now, zone = zone)
        assertEquals(Instant.parse("2026-09-08T22:00:00Z"), next) // 18:00 EDT next day
    }

    @Test
    fun `next trigger at exactly the chosen moment rolls to tomorrow, never fires twice`() {
        val now = Instant.parse("2026-09-07T22:00:00Z") // exactly 18:00 EDT
        val next = ReminderPolicy.nextTrigger(hour = 18, minute = 0, now = now, zone = zone)
        assertEquals(Instant.parse("2026-09-08T22:00:00Z"), next)
    }

    @Test
    fun `rejects an out-of-range hour or minute rather than silently wrapping`() {
        val now = Instant.parse("2026-09-07T12:00:00Z")
        assertFailsWithMessage("hour") { ReminderPolicy.nextTrigger(24, 0, now, zone) }
        assertFailsWithMessage("hour") { ReminderPolicy.nextTrigger(-1, 0, now, zone) }
        assertFailsWithMessage("minute") { ReminderPolicy.nextTrigger(0, 60, now, zone) }
    }

    private fun assertFailsWithMessage(fragment: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "expected IllegalArgumentException, got $error")
        assertTrue(error.message.orEmpty().contains(fragment), "expected message to mention '$fragment': ${error.message}")
    }
}
