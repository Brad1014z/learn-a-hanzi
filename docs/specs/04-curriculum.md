# 04 — Curriculum

> **Status:** DRAFT
> Which characters the app teaches, in what order, and the rules for unlocking them.

## Scope

- **MVP:** **HSK 1** — the ≈178 characters of the HSK 1 vocabulary set.
  (HSK 2.0 list; ⚠ verify against the current official list at Phase 0, since HSK
  restructured into 9 levels in 2021. We target the well-known HSK 1 ≈178 set regardless of
  which "version" it's called.)
- **MVP+:** HSK 2, then HSK 3 (≈150 / ≈300 chars). Added after the MVP loop is validated.
- **Later:** HSK 4–6, a frequency-only track, and user-built custom decks.

Only characters that have **complete data** (stroke paths + medians + a definition) are
teachable. Characters missing any of those are silently excluded and logged by the ingest
tool (see `02`). The curriculum is a *subset* of teachable characters, ordered.

## Ordering within a level

Within HSK 1, characters are ordered by:

1. **Frequency rank** (from Unihan `kFrequency`-derived rank) — most common first, so the
   user immediately learns characters they'll see everywhere.
2. Tie-break: **stroke count ascending** — simpler shapes first (good early morale).
3. Final tie-break: **radical grouping** so visually/structurally similar characters sit
   near each other when stroke count is equal.

This produces a single deterministic `sequence` per level, stored as
`Character.curriculumOrder` (added in `03`; if not, derive on the fly — TBD at implementation).

## Progression rules

The user does **not** get all 178 characters dumped at once. Unlocking is paced:

- A **daily new-character cap** (default **10**, configurable in Settings). The app
  introduces up to `cap` new characters per calendar day.
- A character enters the user's world in the `NEW` state and moves to `LEARNING` on first
  exposure (see SRS states in `03` / `06`).
- **Order is respected**: new characters unlock in `curriculumOrder` sequence. You can't
  jump ahead to character #50 while #5 is still `NEW`. (Browse mode is exempt — see below.)
- Once a character reaches the `REVIEW` state (graduated from learning, per SRS), it no
  longer counts against new-character headroom.

### "Learned" definition

A character is considered **learned** (counts toward "characters mastered") when it is in
the `REVIEW` state **and** has been answered correctly at least twice in review. This is
deliberately stricter than "seen once" so the headline number is meaningful.

## Two tracks

1. **Guided track (default):** the curriculum sequence above. This is what the daily
   "Learn" button does.
2. **Browse mode:** the user can open *any* character in the app (e.g. via search or a
   radical browser) to see its detail and even practice it. Browse practice **does not**
   consume the daily cap and **does not** unlock gated content — it's free exploration.
   If they practice a not-yet-unlocked character in Browse, it still creates a
   `CharacterProgress` row (so they get reviews), it just doesn't reorder the guided track.

## Daily session shape

A typical day for an active user:

1. **Reviews first.** Due cards (from SRS `dueAt ≤ now`) are queued and practiced first.
   These are the highest-value work — the app surfaces them prominently on Home.
2. **Then new characters** up to the remaining daily cap, but only if reviews are not
   backlogged beyond a threshold (configurable; default: if > ~100 reviews due, the app
   suggests clearing them before adding new ones, but doesn't hard-block).
3. **Optional extra practice.** Free practice on any character in Browse.

## Reordering / resets

- **No surprise resets.** If a user lapses, intervals shrink and the card re-enters
  `RELEARNING`, but progress isn't wiped.
- **Manual reset** of all progress is available in Settings (destructive, with
  confirmation) — useful for re-learning or for clearing a portfolio demo.

## What's out of scope

- **No adaptive reordering** based on which characters the user finds hard (possible later;
  the data is captured in `ReviewLog` to enable it). MVP uses the fixed sequence.
- **No multi-character "words" as first-class cards.** Words/sentences are *context* shown
  alongside a character, not separate SRS cards. (Considered for Phase 2.)
- **No grammar sequencing.** Example sentences are chosen for shortness + containing the
  character, not for grammatical progression.

## Curriculum data

The HSK 1 character list + frequency data is part of the seeded dataset (see `02`).
Concretely:

- `Character.hskLevel = 1` for the HSK 1 set.
- `Character.freqRank` drives intra-level order.
- `Character.curriculumOrder` (if added) is a stable integer sequence per level, computed
  by the ingest tool so the app doesn't recompute ordering at runtime.

## Open questions

- [ ] HSK 1 ≈178: confirm the exact character list + counts at Phase 0 (list is stable, but
      pin the source snapshot). ⚠ verify
- [ ] Should the daily cap be "new chars/day" or also limit total cards/day? Lean: only
      new chars; reviews are uncapped (you should always clear your reviews).
