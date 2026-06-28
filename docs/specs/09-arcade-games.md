# 09 — Arcade Games

> **Status:** DRAFT · **Phase 2**
> The game design. All four modes reuse the **existing stroke grader from `05` unchanged** —
> the "game" is a scoring / combo / UX layer on top, plus a competitive layer on top of that.
>
> Arcade is governed by the constitution's **"separation of learning and play"** principle:
> the learning path stays calm and accuracy-focused; speed and adrenaline live only here, in
> a track the user can ignore entirely.

## Design goals

- **Reuses the hardest, most differentiated tech.** The stroke grader (`05`) is the app's
  moat. Every game is built *on* it, not around it. No recognition/matching modes — this is
  and remains a *writing* app.
- **Rewards learning, doesn't replace it.** Game character pools draw from what the user has
  already learned, so Arcade gets richer the more you study.
- **Fully playable offline.** Games never require a network. Only score submission and
  league view (Phase 2b/2c) do. See offline behavior below.
- **Low-stakes competition.** Anonymous leagues, no real rewards — fun motivation, not a
  source of stress.

## Modes

Four modes. Each takes the grader + a target character and wraps it in scoring rules.

### 1. Stroke Sprint (60s)
- **Loop:** write as many complete characters as possible before the 60s timer hits zero.
- **Rules:** a wrong stroke costs a small **time penalty** (e.g. −1.5s) and breaks the combo;
  completing a character grants base points + combo bonus. Sloppy accepts count as complete
  but don't advance the combo.
- **Feel:** pure adrenaline, the headline arcade mode.
- **End:** timer zero → score screen.

### 2. Daily Challenge
- **Loop:** the **same 5 characters for everyone** that day, seeded by date (UTC). Score =
  accuracy × speed across the fixed set.
- **Attempts:** best-of-N attempts per day (N = 3, configurable). Only the best score counts
  toward the daily leaderboard.
- **Feel:** the **social anchor** of the app — a shared daily moment that drives the league.
- **Character set:** drawn from the **global HSK 1 pool** (not the user's learned set) so
  every player faces the same difficulty regardless of progress. New players may see
  characters they haven't learned — that's fine; it's a teaser, and the demo is available.

### 3. Combo Tower (endless)
- **Loop:** characters stream endlessly, getting progressively rarer (frequency descends).
  Clean (non-sloppy, no-hint) characters build the combo multiplier.
- **Rules:** one **failed character** (wrong stroke beyond the retry limit) = **game over**.
  No timer; depth and flow.
- **Feel:** zen + tension; the long-form skill mode.

### 4. Speed Write (time attack)
- **Loop:** a fixed set of N characters (N = 5/10/15); clear them all as fast as possible.
- **Rules:** wrong strokes add a **time penalty**; final time is the score (lower is better,
  converted to points for leaderboard uniformity).
- **Feel:** precision under pressure; the focused practice-arcade hybrid.

## Character pool rule (ties games to learning)

> The more you learn, the more you can play.

- **Stroke Sprint, Combo Tower, Speed Write** draw from the user's **learned + in-review**
  characters (`CharacterProgress.state ∈ {REVIEW, RELEARNING}` and "learned" per `04`).
  Brand-new users have a small pool (the few chars they've just learned) — by design.
- **Daily Challenge** is the exception: it uses the global HSK 1 pool so all players share
  the same daily set (see above).
- If the user's learned pool is too small to fill a game (< ~5 chars), Sprint/Tower/Speed
  offer a **guided fallback**: draw from the first N curriculum characters with the demo
  available on demand. Never a dead end.

## Combo system (shared)

A combo rewards clean, no-hint, in-order strokes:

- Completing a character with **all accepts (no sloppy, no hint, no reject-retry)** →
  `combo++`.
- Multiplier: `1.0 + min(combo, 10) × 0.1` → **caps at 2.0×**.
- **Reset to 0** on: wrong stroke, hint use. In Sprint/Tower also reset on sloppy accept;
  in Speed Write sloppy is tolerated (doesn't reset) to keep the mode flowing.
- The multiplier is applied to that character's point award.

## Scoring

Per-character base points (tunable, in a single `object GameConfig`):

```
basePoints(char) = 100                       // flat per completed character
+ strokeBonus  = 10 × strokeCount            // harder chars worth more
+ cleanBonus   = 25  if no sloppy/reject     // rewards precision
+ speedBonus   = f(timeTakenMs, strokeCount) // only in timed modes
```

- `SessionScore = Σ round(basePoints(char) × comboMultiplier)` over the session.
- **XP awarded ≈ SessionScore** (1:1, clamped to a per-session ceiling to limit farming).

Tuning constants (starting points):

```kotlin
object GameConfig {
    const val SPRINT_DURATION_MS   = 60_000
    const val WRONG_STROKE_PENALTY_MS = 1_500
    const val COMBO_CAP            = 10
    const val COMBO_MULTIPLIER_STEP = 0.1
    const val COMBO_MAX            = 2.0
    const val DAILY_CHALLENGE_CHARS = 5
    const val DAILY_CHALLENGE_ATTEMPTS = 3
    const val XP_PER_SESSION_CEILING = 5_000
    const val LEAGUE_SIZE          = 12
    const val LEAGUE_PROMOTE      = 3
    const val LEAGUE_RELEGATE     = 3
}
```

Like `GradingConfig` in `05`, this lives in `:core:games` and is unit-tested.

## Leagues (Phase 2c — competition layer)

- **Cadence:** weekly. Reset **Monday 00:00 UTC** (scheduled by a Cloud Function — see `10`).
- **Composition:** each league has **~12 anonymous players**, auto-shuffled each week.
- **Identity:** auto-generated handles (e.g. "Swift Otter", "Brave Panda") — **no real
  names, no avatars from contacts, no friend adds**. Re-roll handle on request.
- **Progression:** top 3 promoted, bottom 3 relegated. ~5 tiers (Bronze → … → Master-ish);
  names TBD at implementation.
- **Scoring period XP** (from all game sessions that week) determines placement.
- **Daily Challenge** also has a **per-day leaderboard** (separate from the weekly league),
  reset daily — a faster feedback loop within the week.

## Scoring integrity (low-stakes, best-effort)

We do **not** re-grade strokes on the server. Instead, on score submission the client sends
the score plus a **compact play log**:

```
GameSubmission {
  mode, characterSetId, score, xp,
  startedAt, durationMs,
  events: [ { char, verdict, ts } ... ]   // per-character completion/fail
}
```

The server (Cloud Function) does **bounds validation only**:

- `durationMs` plausible for `mode` + character count (± tolerance).
- `score` within the **theoretical max** for the reported character set and combo rules.
- Event timestamps monotonic and within the session window.
- Rate limit submissions per user per day per mode.

Anything failing validation is **dropped silently** (no ban, no nag) — honest documentation:
this is a fun anonymous ladder, not an esports anti-cheat. The minimal reward + anonymity
keeps the cheat incentive low. See `10` for the protocol.

## Offline behavior

- **Games are fully playable offline.** All four modes run with zero network dependency.
- **Local high scores** are always saved to Room (`LocalHighScore` table — see `03`) and shown
  immediately on the score screen.
- If the user is **not signed in** or **offline**, earned XP is queued in a `PendingXpSync`
  outbox table. On reconnect + sign-in, the outbox drains to the server.
- If you're **offline all week**, you simply don't climb the league that week — **no penalty,
  no streak loss, no nag** (constitution: no guilt-tripping).
- League view requires network; if offline, show last-cached standings with an "offline"
  indicator rather than a spinner.

## Relationship to the learning path

- Playing games **does not** advance or interfere with the SRS learning schedule. Game
  sessions are separate from reviews.
- (Open, deferred) A future "spaced-practice" tie-in could let a strong Combo Tower run
  count as a light review for the characters played — but that muddies the separation
  principle, so it's explicitly **not** in the current plan.

## UX posture

- Arcade feedback is **more energetic** than the learning path: faster flashes, combo
  counters, score popups, punchier haptics. This is the one place the app gets loud.
- The learning path stays exactly as specced in `07` — calm, no timers. The two must feel
  visually distinct (separate theme accents per track is an option — see `07` amendments).

## Open questions

- [ ] League tier names + count (5 tiers? 7?). Decide at implementation.
- [ ] Whether Speed Write should also have a combo or stay combo-free (purity vs uniformity).
      Lean: combo-free; it's the precision mode.
- [ ] Daily Challenge character selection: pure date-seed (deterministic, fair) vs.
      curated-by-hand daily picks (more control, more work). Lean: date-seed.
- [ ] Handle generation: local wordlist (offline, deterministic) vs server-assigned. Lean:
      local wordlist so it works offline.
