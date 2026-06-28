# 00 — Constitution

> **Status:** DRAFT
> The constitution is the highest-level spec. Every other document must be consistent
> with it. If a later spec contradicts something here, the constitution wins — or the
> constitution is amended explicitly.

## Mission

Teach people to **write** Chinese characters correctly, and make each character stick by
reinforcing it with its meaning, pronunciation, and a useful phrase or sentence. Writing
is the spine of the app; everything else exists to support it.

## Why "writing first"

Most Chinese-learning apps lean on recognition (tap the right character). Recognition is
shallow memory. Forcing the hand to reproduce a character — in the right order, with the
right strokes — builds the durable, spatial memory that survives long after the app is
closed. Stroke order also encodes the logic of how characters are constructed, which makes
future characters easier to learn. So: we grade writing, not just recognition.

## Core principles

1. **Offline-first.** The app works fully without a network. All learning content is
   bundled. No feature may hard-depend on a live server. A network is a convenience
   (future sync), never a requirement to learn.

2. **Correctness over speed.** We are not a typing-racer. Practice screens prioritize
   accurate, well-formed strokes and correct order over how fast a user can blaze through.
   Timers, if any, are for the user's own pacing — never a scoring pressure.

3. **Data transparency.** Learning content comes from open datasets (make-me-a-hanzi,
   CC-CEDICT, Unihan, Tatoeba). We name them, attribute them, and keep ingestion
   reproducible. No proprietary content lock-in. A user could rebuild the dataset
   themselves from public sources.

4. **Minimal permissions, minimal footprint.** No accounts required to start. No ads, no
   trackers, no background data collection in the MVP. We ask for only what a feature
   needs.

5. **Feedback that teaches, not punishes.** Grading is forgiving within reason and always
   shows the correct path on failure. The goal is to leave every session having written
   something correctly, not to tally mistakes.

6. **Calm, single-task screens.** One clear thing per screen. No notifications nags, no
   streak guilt-tripping as a dark pattern. Streaks and progress exist to motivate, and
   they say so honestly.

## Design values

- **Large, generous drawing surfaces.** Writing happens with a finger/stylus on a canvas
  that fills the available space; UI chrome gets out of the way.
- **Tone-aware pinyin.** Tone marks are always shown and visually emphasized; tones are
  part of the word, not decoration.
- **Readable typography for definitions and examples.** Both Chinese and English must be
  comfortable to read at a glance, including reasonable font sizing and line height.
- **Dark mode is first-class**, not an afterthought.

## Explicit non-goals

These are things we are **deliberately not building**. Listing them prevents scope creep.

- **Handwriting recognition of arbitrary input.** We grade against a *known target
  character*; we do not attempt to identify what character the user wrote from scratch.
- **A general Chinese course / grammar curriculum.** We teach characters and their
  immediate context, not sentence construction rules, grammar drills, or listening
  comprehension exercises.
- **Social / community features.** No friends, leaderboards-as-pressure, sharing, or
  accounts-required collaboration in the MVP.
- **iOS, web, or desktop.** Android only for the foreseeable future. Architecture stays
  clean enough that sharing logic later is plausible, but we optimize for Android.
- **A custom TTS voice or recorded audio library.** MVP uses the system TTS engine. We do
  not bundle or license recordings.
- **Editor / authoring tools for users.** Content is curated from datasets, not user-
  generated.

## Success criteria (MVP)

A new user, starting from zero, can:

1. Learn all HSK 1 characters (≈178), each through a demo → guided writing → first-review
   flow, entirely offline.
2. Hear pronunciation (system TTS) and read a short English definition + one example
   phrase per character.
3. Return the next day and get a sensible spaced-repetition review queue, with their
   drawing re-graded.

If those three hold, the MVP has succeeded. Everything else is a stretch goal.

## What "done" means for this spec set

The specs are complete enough to begin Phase 0 (data ingestion) and Phase 1 (MVP)
implementation without further design surprises. The two riskiest areas — data formats/
licenses (`02`) and the stroke grading algorithm (`05`) — are specified concretely enough
to estimate, with explicit "verify at ingest" flags where live confirmation is still owed.
