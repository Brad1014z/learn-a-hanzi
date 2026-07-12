package io.github.brad1014z.hanzi.engine.play

import io.github.brad1014z.hanzi.engine.progress.CharacterProgress
import io.github.brad1014z.hanzi.engine.progress.SrsState

/**
 * Collection ranks (spec 04/10): derived ONLY from SRS state — never stored — so they
 * cannot drift from the truth ("juice with honesty", spec 00). Concrete conditions
 * pinned at M3 implementation (spec 04 amendment):
 *
 * - silhouette (NONE): met but not yet reliable (LEARNING, or never seen)
 * - BRONZE: graduated to REVIEW ("can write it")
 * - SILVER: REVIEW with ≥2 consecutive correct answers *after* graduation
 *   (reps ≥ 5: three learning successes + two review successes) — "learned"
 * - GOLD: REVIEW with interval ≥ 21 days — "durable"
 * - dimmed: RELEARNING — the rank it had "asks for practice" until re-proven
 */
enum class Rank { NONE, BRONZE, SILVER, GOLD }

data class RankState(val rank: Rank, val dimmed: Boolean = false)

object Ranks {
    const val SILVER_REPS = 5
    const val GOLD_INTERVAL_DAYS = 21.0

    fun of(p: CharacterProgress?): RankState = when {
        p == null -> RankState(Rank.NONE)
        p.state == SrsState.RELEARNING ->
            // It graduated at least once (a lapse implies REVIEW before); show the
            // floor rank, dimmed, until it re-proves itself.
            RankState(Rank.BRONZE, dimmed = true)
        p.state != SrsState.REVIEW -> RankState(Rank.NONE)
        p.intervalDays >= GOLD_INTERVAL_DAYS -> RankState(Rank.GOLD)
        p.reps >= SILVER_REPS -> RankState(Rank.SILVER)
        else -> RankState(Rank.BRONZE)
    }

    /** Bronze-or-better fraction, the world-unlock metric (spec 04). */
    fun masteryFraction(progress: Collection<CharacterProgress?>): Double {
        if (progress.isEmpty()) return 0.0
        val bronzeUp = progress.count { of(it).let { r -> r.rank != Rank.NONE && !r.dimmed } }
        return bronzeUp.toDouble() / progress.size
    }
}

/**
 * World unlock gating (spec 04): the first world is always open; each next world opens
 * when the previous reaches [threshold] Bronze-or-better. Browse practice is exempt —
 * gating applies to the guided track (new-character feed) only.
 */
fun unlockedWorldCount(masteryByWorld: List<Double>, threshold: Double = 0.8): Int {
    var unlocked = 1
    for (mastery in masteryByWorld) {
        if (mastery >= threshold) unlocked++ else break
    }
    return unlocked.coerceAtMost(masteryByWorld.size.coerceAtLeast(1))
}
