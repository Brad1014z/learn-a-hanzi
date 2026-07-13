package io.github.brad1014z.hanzi.engine.social

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.Flow

/**
 * Friends + the weekly family board (spec 12, M4 free-tier slice): mutual codes only,
 * no free text, weekly XP with a per-session ceiling so grinding can't dominate
 * (spec 10 guardrail, extended socially).
 */
data class Friend(
    val uid: String,
    val displayName: String,
    val avatarId: Int,
    val weeklyXp: Int, // already ceilinged at write time
    val pending: Boolean = false, // requested, not yet confirmed by the other side
)

data class BoardEntry(val displayName: String, val avatarId: Int, val weeklyXp: Int, val isMe: Boolean)

object SocialConfig {
    /**
     * Per-quest ceiling for XP that counts toward the weekly board (spec 10/12).
     * Generous enough that a real full session always counts; tune with real data
     * (spec 12 open question).
     */
    const val SESSION_BOARD_XP_CEILING = 150
}

object Weeks {
    /** ISO week id like "2026-W28" (Mon–Sun, spec 12's board window), in local time. */
    fun weekId(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    /** Board contribution for one finished quest (ceiling applied per session). */
    fun boardXpForSession(sessionXp: Int): Int =
        sessionXp.coerceAtMost(SocialConfig.SESSION_BOARD_XP_CEILING).coerceAtLeast(0)
}

interface SocialRepository {
    /** Confirmed + pending friends; re-emits on remote changes. */
    val friends: Flow<List<Friend>>

    /**
     * Connect by code (spec 12: both sides must act — entering a code creates a
     * pending edge the code's owner confirms). Returns the friend's display name for
     * the confirmation UI, or null if the code doesn't exist.
     */
    suspend fun addFriendByCode(code: String): String?

    suspend fun confirmFriend(uid: String)

    /** Silent removal — no notification to the other side (spec 12). */
    suspend fun removeFriend(uid: String)

    /** This week's board: me + confirmed friends, ceilinged XP, sorted descending. */
    fun weeklyBoard(weekId: String): Flow<List<BoardEntry>>

    /** Publish my ceilinged weekly XP total for [weekId] (client-written, M4 v1). */
    suspend fun publishWeeklyXp(weekId: String, totalXp: Int)
}
