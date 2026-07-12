package io.github.brad1014z.hanzi.engine.play

/**
 * Session XP (spec 10): effort is rewarded in the short loop — every quest step pays,
 * including hard days ("a bad review day still pays"). Levels unlock cosmetics only,
 * never learning content or rank shortcuts. Values are tunable in one place, like
 * GradingConfig — sliders for feelings (spec 11).
 */
object XpConfig {
    const val WARM_UP = 10
    const val REVIEW_CLEARED = 10
    const val NEW_CHARACTER = 15
    const val BOSS_ATTEMPTED = 20
    const val CHEST_OPENED = 15
}

data class LevelProgress(val level: Int, val intoLevel: Int, val neededForNext: Int)

object Levels {
    /** XP needed to go from level L to L+1: 100, 125, 150, … (gentle ramp). */
    fun costOf(level: Int): Int = 100 + 25 * (level - 1)

    fun progressFor(totalXp: Int): LevelProgress {
        require(totalXp >= 0)
        var level = 1
        var remaining = totalXp
        while (remaining >= costOf(level)) {
            remaining -= costOf(level)
            level++
        }
        return LevelProgress(level, remaining, costOf(level))
    }

    fun levelFor(totalXp: Int): Int = progressFor(totalXp).level
}
