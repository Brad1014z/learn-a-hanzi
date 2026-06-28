# 08 — Roadmap

> **Status:** DRAFT
> Phased milestones. Each phase has a clear **definition of done** so "are we there?" is
> answerable. Dates are not committed — this is a personal/portfolio project; quality over
> speed.

## Phase 0 — Foundation

Goal: a buildable repo, locked specs, and a reproducible data pipeline. **No app UI yet.**

- [ ] Specs reviewed and marked `ACCEPTED` (this document set).
- [ ] Gradle multi-module skeleton: `:app`, `:core:data`, `:core:domain`, `:core:ui`,
      `:feature:practice`, `:feature:review`, `:feature:browser`, `:data-ingest`.
      (Phase 2 modules `:core:games`, `:feature:arcade`, `:core:account`, `:core:sync` are
      added in Phase 2a/2b — see below.)
- [ ] Version catalog (`gradle/libs.versions.toml`) with pinned dependency versions.
- [ ] Hilt + Compose + Room wired in `:app` so it launches to a blank screen.
- [ ] `:data-ingest` tool:
  - [ ] Downloads/reads make-me-a-hanzi, CC-CEDICT, Unihan, Tatoeba from `data/raw/`.
  - [ ] ⚠ Verifies actual field names + coordinate space; fails loudly on schema drift.
  - [ ] ⚠ Confirms license terms for each source; records attribution manifest.
  - [ ] Produces `hanzi_vN.sqlite` matching the `03` schema, scoped to HSK 1.
  - [ ] Emits an ingest report (counts, drops, dataset version).
- [ ] Bundled asset copied into the app on first launch; `Meta.datasetVersion` readable.

**Definition of done:** running `./gradlew :data-ingest:run` regenerates the SQLite asset
deterministically, and `:app` launches offline showing the dataset version.

## Phase 1 — MVP

Goal: the full learn → practice → review loop, offline, for HSK 1. *(Constitution's success
criteria.)*

- [ ] **Data layer:** Room entities, DAOs, repositories (Character, Progress, Curriculum,
      Settings) with Flow APIs; in-memory + migration tests.
- [ ] **Domain:** SRS engine (SM-2 + state machine) fully unit-tested; use cases for
      "next new character", "due queue", "submit review".
- [ ] **Stroke engine** (`:feature:practice`): renderer + demo animation + quiz grading per
      `05`; golden-path stroke corpus + tests; `GradingConfig` tuned.
- [ ] **Screens:** Home, Character Detail, Practice, Review, Browse (search + HSK 1 list),
      minimal Settings.
- [ ] **Audio:** TTS wired with graceful no-engine fallback.
- [ ] **Theme:** light/dark, semantic colors, rice-grid guide.
- [ ] **Curriculum:** HSK 1 ≈178 chars, frequency-ordered, daily cap (default 10),
      unlock gating.
- [ ] Smoke test: a fresh user can learn all HSK 1 characters and review them next day.

**Definition of done:** a new user can learn all ~178 HSK 1 characters with stroke-order
feedback, hear pronunciation, read a definition + one example, and get a correct SRS review
queue the next day — fully offline.

## Phase 2 — Polish, depth & Arcade

Goal: make the app genuinely pleasant, deepen content, and add a fun optional **Arcade** +
**competition** layer. Phase 2 is split so the riskier, backend-dependent parts are isolated.

> Constitutional reminder: the calm offline learn→practice→review loop from Phase 1 stays
> first-class throughout. Arcade is an additive track (spec `09`); competition is opt-in
> and anonymous (specs `09`/`10`).

### Phase 2a — Local Arcade (no backend)

Goal: the four writing-based game modes, playable fully offline with local high scores.

- [ ] `:core:games` module: scoring formula, combo math, theoretical-max calc, league
      promotion/relegation logic — fully unit-tested (mirrors SrsEngine rigor).
- [ ] `:feature:arcade`: Arcade hub + shared game screen shell reusing the stroke grader.
- [ ] Modes: Stroke Sprint (60s), Combo Tower (endless), Speed Write (time attack).
  Daily Challenge UI can exist but is local-only until 2b.
- [ ] Local state: `GameSession`, `LocalHighScore` tables (see `03`).
- [ ] Distinct energetic Arcade visual identity vs the calm learning path (see `07`).
- [ ] Character-pool rule: games draw from learned + in-review chars (`09`).

**Definition of done:** a user can play all four modes offline and see local high scores;
the learning path is untouched.

### Phase 2b — Daily Challenge + leaderboards (backend, no leagues yet)

Goal: the social anchor — a shared daily challenge and per-day leaderboards.

- [ ] Backend vendor decision: Firebase (recommended) vs Supabase — confirm here.
- [ ] Google Sign-In (`:core:account`), profile creation with auto-generated handle.
- [ ] `:core:sync`: `ScoreRepository` + outbox drain via WorkManager; score validation
      Cloud Function (bounds-check only).
- [ ] Daily Challenge: date-seeded shared set; best-of-N attempts; per-day leaderboard.
- [ ] `PendingXpSync` outbox; offline submission queues and drains on reconnect.
- [ ] Sign-in/out + account-delete in Settings; privacy policy drafted.

**Definition of done:** signed-in users see a shared Daily Challenge and today's leaderboard;
unsigned/offline users lose nothing (local play + high scores intact).

### Phase 2c — Weekly anonymous leagues

Goal: the long-term competition loop.

- [ ] Weekly league shuffle Cloud Function (cron, Mon 00:00 UTC), ~12-person leagues.
- [ ] `LeagueRepository`; league standings view (cached, offline-aware).
- [ ] Promotion/relegation (~3 up / ~3 down), tier ladder.
- [ ] Handle re-roll; weekly XP aggregation from all game sessions.
- [ ] "Offline / stale" indicators; no penalties or guilt-tripping for missing a week.

**Definition of done:** signed-in users are placed in a weekly anonymous league and
promoted/relegated based on their game XP; unsigned users see none of it.

### Phase 2 — Polish & depth (parallel, non-backend)

Goal: make the core learning app genuinely pleasant and deeper. Can interleave with 2a–2c.

- [ ] Richer example sentences (multiple per character, curated shortlist).
- [ ] Audio polish: better TTS handling, per-word/per-sentence playback.
- [ ] Progress dashboard (heatmap, retention chart, mastered count over time).
- [ ] Custom decks / favorites ("star" a character).
- [ ] Frequency-only track + HSK 2/3 characters.
- [ ] Outline-clip demo animation (prettier than the median-stroke MVP demo).
- [ ] Radical browser + decomposition navigation.
- [ ] Onboarding screen + adaptive icon.
- [ ] Performance pass + robustness (large decks, edge cases).

**Definition of done (whole Phase 2):** the app feels finished for daily use; Arcade is fun
and ignorable; competition is opt-in and honest; content extends beyond HSK 1.

## Phase 3 — If we go further (optional)

Goal: take it from personal to publishable, if desired.

- [ ] Privacy-respecting, **opt-in** crash reporting.
- [ ] Cloud sync of *learning* progress (separate from game sync; layer over existing
      repository interfaces).
- [ ] Backup/restore (export/import a small JSON of progress).
- [ ] Achievements / milestones (honest, non-dark-pattern).
- [ ] Wider curricula (HSK 4–6), traditional-character support.
- [ ] Play Store listing, screenshots, privacy policy, data-attribution credits screen.
- [ ] Optional recorded audio track (if licensing + size allow).

**Definition of done:** shippable to the Play Store with a clear conscience on privacy and
data licensing.

## Cross-cutting (always)

- Every phase keeps `:core:domain` pure and well-tested; SRS + grading are the trust core.
- Every phase respects the constitution: offline-first, minimal permissions, no trackers,
  data transparency.
- ⚠ verify flags in `02` (licenses, formats) are resolved before the *first public*
  distribution, not before internal use.

## What we're explicitly not scheduling

- iOS / web / desktop ports.
- A custom TTS voice or recorded-audio production pipeline (only revisited in Phase 3, and
  only if it stays cheap).
- User-generated content / social features.
- Grammar curriculum.
