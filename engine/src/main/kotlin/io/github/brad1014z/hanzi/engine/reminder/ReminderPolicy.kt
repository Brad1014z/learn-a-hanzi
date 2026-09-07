package io.github.brad1014z.hanzi.engine.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The opt-in daily reminder (spec 10 guardrail 5: "notifications, if ever added, are
 * opt-in and neutral"; spec 12's sign-in pattern is the model — a calm entry point,
 * never a nag, never repeated after dismissal). This object holds the two decisions
 * that must stay honest regardless of how the platform code around them changes:
 *
 * 1. Whether *today's* scheduled tick should actually notify.
 * 2. When the *next* tick should fire, so the reminder lands near the time the learner
 *    picked rather than near whenever the last WorkManager run happened to execute.
 */
object ReminderPolicy {

    /** The single neutral message (spec 10): no streak talk, no urgency, no guilt. */
    const val MESSAGE: String = "Your quest is ready"

    /**
     * A day already played needs no nudge — the whole point is reminding the learner to
     * show up, not badgering someone who already did (constitution: no guilt mechanics).
     */
    fun shouldNotify(enabled: Boolean, playedToday: Boolean): Boolean = enabled && !playedToday

    /**
     * The next occurrence of [hour]:[minute] at or after [now], in [zone] — today if that
     * time hasn't passed yet, tomorrow otherwise. Pure and zone-explicit so it is testable
     * without depending on the device's timezone or clock.
     */
    fun nextTrigger(hour: Int, minute: Int, now: Instant, zone: ZoneId): Instant {
        require(hour in 0..23) { "hour must be 0..23, was $hour" }
        require(minute in 0..59) { "minute must be 0..59, was $minute" }
        val nowZoned = ZonedDateTime.ofInstant(now, zone)
        val todayAt = nowZoned.with(LocalTime.of(hour, minute, 0, 0))
        val next = if (todayAt.isAfter(nowZoned)) todayAt else todayAt.plusDays(1)
        return next.toInstant()
    }
}
