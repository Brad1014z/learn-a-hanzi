# 00 — Constitution

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-05 — play-layer reframe, see `10`)
> The constitution is the highest-level spec. Every other document must be consistent
> with it. If a later spec contradicts something here, the constitution wins — or the
> constitution is amended explicitly.

## Mission

Teach people to **write** Chinese characters correctly, and make each character stick by
reinforcing it with its meaning, pronunciation, and a useful phrase or sentence. Writing
is the spine of the app; everything else exists to support it.

And it is **a game**: characters come alive in your collection as you learn to write
them. Worlds, quests, XP, and a daily challenge are the frame; honest learning is what
they measure (see `10-play-layer.md`). The tone is **all-ages playful** — kids genuinely
enjoy it, adults are never condescended to.

## Ambition

A real, published product — **free and open**: open-source code, Play Store distribution,
no ads, no trackers, no paywalls. Not a portfolio exercise. This raises the bar on data
licensing (we comply with share-alike terms and ship attribution) and justifies a
**portable core** (see design values) for the platforms and languages that may follow
Android + Simplified Chinese.

It is also a **father-son build**: the project is co-developed with a 12-year-old
co-creator who owns the game-feel workstream. The build itself is a deliverable — a
gateway into AI-assisted coding, CS basics, and game design (see
`11-family-prototype.md`).

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

2. **Correctness over speed — where it matters: the stroke.** The moment of writing is
   protected: while learning or reviewing, no countdown ever runs against a moving hand,
   and accuracy always beats pace. Around that protected moment, the app is unapologetically
   a game. Timed play exists only in the **opt-in arcade**, and only over characters the
   user has already mastered — speed is celebration of mastery, never a gate to learning
   (see `10-play-layer.md`).

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

6. **Focused writing, joyful everything else.** During a stroke: one clear thing, no
   noise. Between strokes and around sessions: celebration is encouraged — XP, confetti,
   a growing collection. Still banned, forever: guilt mechanics, fake urgency, lives/energy
   that block learning, pay-to-progress, notification nagging, and streak shaming (streaks
   count days played and pause gracefully). Progress signals say what they mean, honestly.

## Design values

- **Large, generous drawing surfaces.** Writing happens with a finger/stylus on a canvas
  that fills the available space; UI chrome gets out of the way.
- **Tone-aware pinyin.** Tone marks are always shown and visually emphasized; tones are
  part of the word, not decoration.
- **Readable typography for definitions and examples.** Both Chinese and English must be
  comfortable to read at a glance, including reasonable font sizing and line height.
- **Dark mode is first-class**, not an afterthought.
- **Portable core.** Domain logic — the SRS engine, stroke-grading math, curriculum rules —
  is pure Kotlin with no `android.*` imports, isolated in modules that can become Kotlin
  Multiplatform later. UI and platform glue stay native and thin. This is the cheap
  insurance behind the iOS and Japanese/Korean options in `09-extension-paths.md`.
- **Juice with honesty.** Game feedback is generous (animation, sound, haptics, confetti)
  but every game *signal* — ranks, worlds, XP — reflects true learning state. Nothing
  glitters that isn't actually known (see `10-play-layer.md` for the gating rules).

## Explicit non-goals

These are things we are **deliberately not building**. Listing them prevents scope creep.

- **Handwriting recognition of arbitrary input.** We grade against a *known target
  character*; we do not attempt to identify what character the user wrote from scratch.
- **A general Chinese course / grammar curriculum.** We teach characters and their
  immediate context, not sentence construction rules, grammar drills, or listening
  comprehension exercises.
- **Social / community features.** No friends, global leaderboards, or accounts-required
  collaboration in the MVP. (The daily challenge's **share card** is OS-level sharing of
  a result image/text — no server, no accounts, no in-app social graph — and is allowed.)
- **iOS, web, or desktop — in the MVP.** Android ships first, alone. iOS is a real
  candidate *after* the Android MVP validates; the pure-Kotlin core stays KMP-ready to keep
  that option cheap (see `09-extension-paths.md`). No cross-platform UI framework
  regardless.
- **Japanese / Korean — in the MVP.** The engine and pipeline are designed to generalize
  (KanjiVG is a drop-in analog for kanji), and the extension path is documented in
  `09-extension-paths.md`, but MVP content is Simplified Chinese only.
- **A custom TTS voice or recorded audio library.** MVP uses the system TTS engine. We do
  not bundle or license recordings.
- **Editor / authoring tools for users.** Content is curated from datasets, not user-
  generated.

## Success criteria (MVP)

A new user, starting from zero, can:

1. Learn all HSK 1 characters (~174 unique characters from the 150-word list), each
   through a demo → guided writing → first-review flow, entirely offline.
2. Hear pronunciation (system TTS) and read a short English definition + one example
   phrase per character.
3. Return the next day and get a sensible spaced-repetition review queue, with their
   drawing re-graded.
4. **Want to come back.** The daily quest, XP, and collection make returning feel like
   play — validated the honest way: our in-house 12-year-old playtester returns without
   being asked.

If those four hold, the MVP has succeeded. Everything else is a stretch goal.

## What "done" means for this spec set

The specs are complete enough to begin Phase 0 (stroke-engine prototype) and the phases
beyond (data pipeline, MVP — see `08-roadmap.md`) without further design surprises. The
two riskiest areas — data formats/licenses (`02`) and the stroke grading algorithm (`05`)
— are specified concretely enough to estimate, with explicit "verify at ingest" flags
where live confirmation is still owed.
