# 04 — Curriculum

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-05 — worlds & play layer, see `10`)
> Which characters the app teaches, in what order, and the rules for unlocking them.

## Scope

- **MVP:** **HSK 1** — the unique characters of the HSK 1 (2.0) 150-word vocabulary
  set. *(Pinned at M2, 2026-07-10: `data/pinned/hsk1-words.json` — 150 words,
  **178 unique characters**. The oft-quoted "~174" varies by counting convention; the
  pinned file is our truth.)* HSK restructured into 9 levels in 2021; we target the
  well-known HSK 2.0 level-1 set regardless of what it's called now.
- **MVP+:** HSK 2, then HSK 3 (≈150 / ≈300 chars). Added after the MVP loop is validated.
- **Later:** HSK 4–6, a frequency-only track, and user-built custom decks.

Only characters that have **complete data** (stroke paths + medians + a definition) are
teachable. For characters *inside* an MVP curriculum level, missing data **fails the
ingest build** — the constitution promises "all of HSK 1", so a silent drop is a broken
promise. Outside the curricula, incomplete characters are excluded and logged (see `02`).
The curriculum is a *subset* of teachable characters, ordered.

## Ordering within a level

Within HSK 1, characters are ordered by:

1. **Frequency rank** (Tatoeba-corpus-derived, computed at ingest — see `02`; Unihan's
   `kFrequency` no longer exists) — most common first, so the user immediately learns
   characters they'll see everywhere.
2. Tie-break: **stroke count ascending** — simpler shapes first (good early morale).
3. Final tie-break: **radical grouping** so visually/structurally similar characters sit
   near each other when stroke count is equal.

This produces a single deterministic sequence per level, stored as
`CurriculumEntry.sequence` (see `03`), computed by the ingest tool so the app never
re-derives ordering at runtime.

### Worlds (play layer)

The play layer (`10-play-layer.md`) groups each level's characters into **thematic
worlds** — hand-curated clusters for HSK 1, e.g. *Nature* (火 水 山 木 日 月…), *People*
(人 你 我 他…), *Numbers* (一 二 三…). Rules:

- Worlds are ordered by their aggregate frequency (most-common cluster first); **within**
  a world, characters follow `sequence` order as above.
- The next world unlocks when the previous reaches a mastery threshold (default: **80%
  of its characters at Bronze rank or better** — see ranks below). Unlocking is a
  celebration moment, never a paywall or timer.
- The world tags are curated once for HSK 1 (~174 chars) and shipped in the dataset.
  *Schema delta:* a `world` column on `CurriculumEntry`, added to `03` at implementation.

## Progression rules

The user does **not** get all ~174 characters dumped at once. Unlocking is paced:

- A **daily new-character cap** (default **10**, configurable in Settings). The app
  introduces up to `cap` new characters per calendar day.
- A character enters the user's world in the `NEW` state and moves to `LEARNING` on first
  exposure (see SRS states in `03` / `06`).
- **Order is respected within a world**: new characters unlock in `CurriculumEntry.sequence`
  order inside the current world; the next world opens at the mastery threshold above.
  You can't jump ahead to character #50 while #5 is still `NEW`. (Browse mode is exempt —
  see below.)
- Once a character reaches the `REVIEW` state (graduated from learning, per SRS), it no
  longer counts against new-character headroom.

### "Learned" definition & collection ranks

A character is considered **learned** (counts toward "characters mastered") when it is in
the `REVIEW` state **and** has been answered correctly at least twice in review. This is
deliberately stricter than "seen once" so the headline number is meaningful.

The play layer surfaces this as **collection ranks** — derived *only* from SRS state
(the "juice with honesty" value in `00`):

| Rank | Meaning | SRS condition |
|------|---------|---------------|
| — (silhouette) | met, not yet reliable | `NEW` / `LEARNING` |
| **Bronze** | can write it | graduated to `REVIEW` |
| **Silver** | learned | `REVIEW` + ≥2 correct reviews (the definition above) |
| **Gold** | durable | interval ≥ 21 days |

A lapse **dims** the character's rank in the collection (it visibly "asks to be practiced
again") but never deletes it — honest, not punishing. Ranks are recomputed from
`CharacterProgress`; no separate rank state is stored.

## Two tracks

1. **Guided track (default):** the curriculum sequence above. This is what the daily
   "Learn" button does.
2. **Browse mode:** the user can open *any* character in the app (e.g. via search or a
   radical browser) to see its detail and even practice it. Browse practice **does not**
   consume the daily cap and **does not** unlock gated content — it's free exploration.
   If they practice a not-yet-unlocked character in Browse, it still creates a
   `CharacterProgress` row (so they get reviews), it just doesn't reorder the guided track.

## Daily session shape — the daily quest

The daily session is framed as the **daily quest** (`10-play-layer.md`); the underlying
order is unchanged from sound SRS practice:

1. **Warm-up.** One easy due card (highest-retention due character) — a guaranteed early
   win to open the session.
2. **Reviews.** Remaining due cards (from SRS `dueAt ≤ now`). These are the highest-value
   work — the quest presents them as its main body.
3. **New characters** up to the remaining daily cap, but only if reviews are not
   backlogged beyond a threshold (configurable; default: if > ~100 reviews due, the app
   suggests clearing them before adding new ones, but doesn't hard-block).
4. **Boss stroke.** The quest closes with one character drawn fully from memory (no
   guide) — picked from today's material. Completing it opens the **chest**: session XP,
   celebration, and the day's share card. Failing it costs nothing (the character just
   re-queues); the chest still opens on quest completion.
5. **Optional extra practice.** Free practice on any character in Browse; the opt-in
   arcade (M5) lives outside the quest.

## Reordering / resets

- **No surprise resets.** If a user lapses, intervals shrink and the card re-enters
  `RELEARNING`, but progress isn't wiped.
- **Manual reset** of all progress is available in Settings (destructive, with
  confirmation) — useful for re-learning or for clearing a portfolio demo.

## What's out of scope

- **No adaptive reordering** based on which characters the user finds hard (possible later;
  the data is captured in `ReviewLog` to enable it). MVP uses the fixed sequence.
- **No multi-character "words" as first-class cards.** Words/sentences are *context* shown
  alongside a character, not separate SRS cards. (Considered for M5, polish.)
- **No grammar sequencing.** Example sentences are chosen for shortness + containing the
  character, not for grammatical progression.

## Curriculum data

The HSK 1 character list + frequency data is part of the seeded dataset (see `02`).
Concretely:

- `CurriculumEntry(curriculumId = "hsk", level = 1, …)` rows for the HSK 1 set.
- `Character.freqRank` (Tatoeba-derived) drives intra-level order.
- `CurriculumEntry.sequence` is the stable integer teaching order, computed by the ingest
  tool so the app doesn't recompute ordering at runtime.

## Open questions

- [x] ~~HSK 1 ~174: confirm the exact character list + count at M2~~ — **resolved
      2026-07-10: 178 unique characters, pinned in `data/pinned/hsk1-words.json`;
      the ingest tool hard-fails if any lacks complete data (all 178 pass).**
- [x] ~~Cap new chars/day or total cards/day?~~ — **decided: cap only new characters;
      reviews are uncapped** (you should always clear your reviews; the >100-due backlog
      nudge above is the pressure valve).
