# 10 — Play Layer

> **Status:** ACCEPTED (2026-07-05; revised 2026-07-06 — badges added, cloud play layer
> scheduled via `12`)
> The game design of the app. The constitution (`00`) was amended to make the app a game
> outright; this spec defines the game — its economy, its modes, and the guardrails that
> keep it honest. The SRS engine (`06`) and curriculum (`04`) stay the source of truth;
> the play layer is a *presentation and motivation layer over real learning state*, never
> a second, parallel progression.

## The frame

**Your writing brings characters to life.** Every character you learn to write joins your
**collection**; writing it well over time ranks it up; the curriculum unfolds as **worlds**
of related characters; each day offers a **quest** and a shareable **daily challenge**.
Hanzi are pictographic — 火 *is* a fire, 山 *is* a mountain — so the collection has
built-in personality that most games have to invent.

Tone: all-ages playful (`00`, `07`). The working metaphors ("collection", "worlds",
"quest", "boss stroke", "chest") are placeholders — **naming is the 12-year-old
co-designer's job** (see `11`).

## The economy — two currencies, wired differently

The hybrid rule (decided 2026-07-05): **effort is rewarded in the short loop; mastery
gates the long game.** The two must never be confused:

### 1. Session XP (effort — generous, daily)

- Completing daily-quest steps earns XP: warm-up, each review cleared, each new character
  met, boss stroke attempted (not necessarily aced), chest opened.
- XP accumulates into a **learner level**. Levels unlock **cosmetics only** — ink colors,
  canvas themes, celebration styles. Never learning content, never rank shortcuts.
- XP is intentionally hard to "lose": a bad review day still pays, because showing up to
  hard reviews *is* the work. This keeps difficult days rewarding (the failure mode of
  strict-mastery systems, especially for kids).

### 2. Mastery (gates — strict, truthful)

- **Collection ranks** per character — silhouette → Bronze → Silver → Gold — derive
  *only* from SRS state; the exact mapping lives in `04`. A lapse dims the rank until
  the character is re-proven. Ranks are computed from `CharacterProgress`, never stored
  separately, so they cannot drift from the truth.
- **World unlocks** gate on the previous world's mastery threshold (default 80% Bronze+,
  see `04`). No XP amount, purchase, or grind can open a world.
- **Arcade eligibility** gates on mastery: timed modes only ever draw from characters at
  Bronze or better.

## The daily quest

Defined in `04` (curriculum owns session shape): warm-up → reviews → new characters →
boss stroke → chest. Play-layer notes:

- The quest is **always completable** — its size derives from the user's own due queue
  and cap. There is no "you must do X or lose Y."
- The **boss stroke** (one character from today's material, written fully from memory,
  no guide) exists for the end-of-session peak moment. Failing it costs nothing but the
  re-queue; the chest opens on quest completion regardless.
- The **chest** is the celebration bundle: session XP tally, any rank-ups replayed, and
  the day's share card. Big, short (≤ ~1.5s), skippable (`07`).
- Missing a day: nothing is lost, nothing shames. The streak counts **days played** and
  pauses gracefully (`00`).

## The daily challenge (shareable)

Wordle-shaped, offline, serverless:

- Everyone gets the **same character puzzle each day**, derived deterministically from
  the date (hash of `yyyy-mm-dd` over the eligible character set — no server, works in
  airplane mode, consistent across devices).
- Eligible set: a curated pool of common characters (not gated by the user's progress;
  the challenge is a fun test, not curriculum). One attempt per day; write it from
  memory; per-stroke verdicts recorded.
- **Share card:** spoiler-free — day number + emoji grid of per-stroke verdicts
  (🟩 clean / 🟨 sloppy / 🟥 miss), never the character itself. Shared via the OS share
  sheet: no accounts, no server, no in-app social graph (`00` non-goals hold).

## The arcade (opt-in, M5)

A clearly separated corner where **speed is celebration of mastery** — entirely local,
entirely offline, no account, no server. (This section absorbs and simplifies an earlier,
independently-drafted backend-and-leagues design; see *Deliberately dropped*, below.)

### Modes

Each mode wraps the unmodified stroke grader (`05`) in a scoring/combo layer — the grader
itself knows nothing about "modes."

- **Stroke Sprint (60s):** write as many complete characters as possible before the timer
  hits zero. A wrong stroke costs a small time penalty and breaks the combo; a clean
  character (no sloppy, no hint, no reject-retry) grows it.
- **Combo Tower (endless):** characters stream one after another, rarity descending
  (commoner characters first). One character failed beyond the retry limit ends the run —
  no timer; it's about depth and flow, not speed.
- **Speed Write (time attack):** a fixed set (5/10/15 characters); clear them all as fast
  as possible. Wrong strokes add a time penalty; final time is the score (lower is better).
- **Daily challenge** is *not* a fourth arcade mode — it's the one already specified above,
  which stays offline/serverless with its own share card. The arcade modes are separate,
  session-length play, not the daily ritual.

### Combo & scoring (local, tunable — mirrors `GradingConfig`'s discipline)

A combo rewards clean, no-hint, in-order stroke sets:

```kotlin
object ArcadeConfig {
    const val SPRINT_DURATION_MS      = 60_000
    const val WRONG_STROKE_PENALTY_MS = 1_500
    const val COMBO_CAP               = 10
    const val COMBO_MULTIPLIER_STEP   = 0.1   // multiplier = 1.0 + min(combo, CAP) × STEP
    const val COMBO_MAX               = 2.0
}
```

- Combo resets on a wrong stroke or a used hint (mode-tuning decides whether a *sloppy*
  accept also resets it — lean: yes in Sprint/Tower, no in Speed Write, to keep it flowing).
- `basePoints(char) = 100 + 10 × strokeCount + (25 if clean)`; session score is the sum of
  `basePoints × comboMultiplier` per character. Purely a **local high score** per mode
  (Room, one row per mode) — cosmetic, no XP conversion, no server, no leaderboard.

### Character pool rule (ties arcade to real progress)

Draws only from characters at **Bronze rank or better** (mastery-gated, per `04`) — the
more you've genuinely learned, the more you can play. If that pool is too small
(new users, < ~5 characters), fall back to the first few curriculum characters with the
demo available on demand, so the mode is never a dead end.

### Rules

- Arcade results never touch SRS state or `CharacterProgress` — playing a game is not a
  review. Scores are **local personal bests only** (per mode): no global leaderboards, no
  accounts, no cloud sync.
- The learn/review writing moment remains untimed, always (`00` principle 2). Arcade is
  the *only* place a clock runs against a stroke, and only over what's already mastered.

## Badges (M5 locally; backed up by the cloud layer)

Badges are the honest trophy case: every badge derives from real recorded state, is
computed **locally** (signed-out users earn everything), never gates content, and is
displayed on a shelf inside the Collection screen (`07`). Taxonomy:

| Category | Examples | Derived from |
|----------|----------|--------------|
| **Mastery** | world completed; 10/50/all characters at Silver; first Gold; all-Gold world | `CharacterProgress` ranks (`04`) |
| **Skill** | 10/50 clean grade-5 characters; a full world learned without hints | `ReviewLog` grades |
| **Consistency** | 7/30/100 days *played* (pauses gracefully, never shames); **comeback** — returning after a break (framed as a win, not an apology) | days-played history |
| **Explorer** | first Browse practice; every world sampled; daily challenge 7-day run | activity events |
| **Social** (signed-in only) | first friend connected; first challenge completed; 5 challenge wins | `12` challenge records |

Rules: badge criteria are **transparent** (each badge shows exactly what earned it or
what's left); no badge is time-pressured or loss-framed; earning one is a celebration
moment (`07` motion rules). The full badge list is finalized at implementation — the
categories and honesty rules above are the contract. Naming/art: co-designer territory
(`11`).

## The cloud play layer (scheduled — see `12`)

An earlier standalone draft (closed PR #1: Firebase, anonymous leagues, XP outbox) was
parked because the constitution then banned accounts outright. The constitution was
**amended 2026-07-06** to the optional-account model, and the bounded version is now
scheduled as roadmap milestone M4, specified in `12-accounts-social.md`:

- **Friends challenges** (mutual codes only, no free text): async score duels on the
  daily challenge and fixed practice sets, plus a weekly friends XP board.
- **What's comparable is bounded:** daily-challenge scores, duel results, and weekly XP
  *with a per-session ceiling* — so grinding can't dominate a friends board, and the
  mastery-truthfulness guardrail extends socially: you can't buy or grind your way past
  a friend who actually learned more.
- **Arcade scores stay local personal bests** — they never enter social comparison.
- Anonymous **leagues** (the PR #1 design) remain archived as the natural second act
  after friends challenges prove out.

## Guardrails (hard constraints — every phase, every feature)

1. **No countdown against a moving hand** in learn/review modes. Ever.
2. **Every visible game signal is mastery-truthful.** Ranks, worlds, and unlocks derive
   from SRS state; XP is visibly labeled as effort, not knowledge.
3. **Cosmetics-only XP store.** Learning content is never locked behind XP, and XP never
   unlocks rank or world shortcuts.
4. **No lives, energy, or cooldowns** that block learning. You can always practice.
5. **No guilt mechanics.** Streaks pause gracefully; lapsed characters "ask" for
   practice, they don't scold; notifications (if ever added) are opt-in and neutral.
6. **No monetization pressure** — free/open product; no ads, no IAP (`00`).
7. **Celebrations are short and skippable** (`07`) — juice serves the loop, not vice versa.

## Data notes (deltas applied at implementation)

- `CurriculumEntry.world` (content) — world tag, curated at ingest (`04`).
- Session XP total + learner level — stored in the `Meta` user keys (pinned at M3:
  `xpTotal`; level is derived, never stored). XP values live in one tunable object
  (`XpConfig`: warm-up 10, review 10, new character 15, boss attempted 20, chest 15)
  and the level curve is `cost(L) = 100 + 25·(L−1)` — sliders for feelings (`11`).
- Daily-challenge attempts — reuse `ReviewLog` with a `session` tag (`"daily-YYYY-MM-DD"`),
  no new table expected.
- `LocalHighScore(mode TEXT PRIMARY KEY, bestScore INTEGER, achievedAt INTEGER)` — one row
  per arcade mode, local-only, added at M5 implementation.
- Everything else (ranks, world progress, dimming) is **derived**, not stored.

## Open questions

- [ ] Names for collection/worlds/quest/boss/chest — the co-designer decides (`11`).
- [ ] World curation for HSK 1: hand-tag ~174 chars into 8–12 thematic worlds (one-time
      content task at M2 ingest).
- [ ] Collection art style (M5 pass): pictograph-flavored glyph art vs. mascot
      illustrations — prototype both cheaply in Phase 0 sessions.
- [ ] Daily-challenge eligible pool: HSK 1 only at first, or all characters with data?
      Lean: HSK 1 pool until M5.
