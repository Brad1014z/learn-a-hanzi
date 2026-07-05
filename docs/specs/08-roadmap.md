# 08 — Roadmap

> **Status:** ACCEPTED (reviewed 2026-07-05)
> Phased milestones. Each phase has a clear **definition of done** so "are we there?" is
> answerable. Dates are not committed — quality over speed — but the destination is a
> published, free/open product (constitution).

## Phase 0 — Stroke-engine prototype

Goal: validate the single riskiest component — **does grading feel right on a real
device?** — before building any infrastructure. One screen, no Room, no SRS, no nav.

- [x] Specs reviewed and marked `ACCEPTED` (2026-07-05, this document set).
- [ ] Single-module Compose app. The *app structure* is throwaway; the **engine code is
      not** — it's written as if already in `:core:domain`: pure Kotlin, no `android.*`
      imports (see `06`).
- [ ] ~10–20 HSK 1 characters as checked-in per-character JSON from hanzi-writer-data
      (same lineage/license as make-me-a-hanzi; zero pipeline work — see `02`). The set
      deliberately includes tricky shapes: dots, hooks, crossings (e.g. 一 人 我 火 心 小 乙).
- [ ] Vendored pure-Kotlin SVG-path parser + Y-flip/normalization applied at load.
- [ ] Practice canvas: rice-grid guide, demo animation (thick-median variant), quiz mode
      with the full grading pipeline per `05` (capture → accidental-contact filter → RDP →
      resample → length guard → position + direction scores → verdict tiers → hint/undo).
- [ ] `GradingConfig` v0 tuned on-device (finger, at least two screen sizes).
- [ ] Golden stroke corpus started: recorded attempts (clean / sloppy / wrong-order /
      wrong-stroke) checked in with expected verdicts + a replay unit test.

**Definition of done:** two or three people can each learn a character they didn't know on
a physical phone, and verdicts feel fair — no false rejects on honest attempts, no false
accepts on wrong-order strokes — measured against the corpus, tuned by feel.

## Phase 1 — Foundation & data pipeline

Goal: a buildable multi-module repo and a reproducible data pipeline; the prototype's
engine moves into its permanent home.

- [ ] Gradle multi-module skeleton: `:app`, `:core:data`, `:core:domain`, `:core:ui`,
      `:feature:practice`, `:feature:review`, `:feature:browser`, `:data-ingest`.
      `:core:domain` is a plain `kotlin("jvm")` module (see `06`).
- [ ] Version catalog (`gradle/libs.versions.toml`) with pinned dependency versions.
- [ ] CI: GitHub Actions — assemble + unit tests on every push.
- [ ] Prototype engine code + golden corpus land in `:core:domain` unchanged, tests green.
- [ ] Hilt + Compose + Room wired in `:app` so it launches to a blank screen.
- [ ] `:data-ingest` tool (see `02` for the full pipeline):
  - [ ] Reads make-me-a-hanzi (`skishore/makemeahanzi`), CC-CEDICT, Unihan, Tatoeba, and
        the pinned HSK 1 list from `data/raw/`.
  - [ ] ⚠ Verifies actual field names; validates + applies the Y-flip normalization;
        fails loudly on schema drift.
  - [ ] Computes Tatoeba-derived frequency ranks; emits `CurriculumEntry` rows for HSK 1
        (~174 chars); tags content `lang = "zh-Hans"`.
  - [ ] **Hard-fails** if any HSK 1 character lacks complete data.
  - [ ] Produces `hanzi_vN.sqlite` matching the `03` schema.
  - [ ] Emits the ingest report + attribution manifest (APL + LGPL texts, CEDICT/Unicode
        attributions, per-sentence Tatoeba contributors).
- [ ] Bundled asset copied into the app on first launch; `Meta.datasetVersion` readable.

**Definition of done:** running `./gradlew :data-ingest:run` regenerates the SQLite asset
deterministically; `:app` launches offline showing the dataset version; CI is green.

## Phase 2 — MVP

Goal: the full learn → practice → review loop, offline, for HSK 1. *(Constitution's success
criteria.)*

- [ ] **Data layer:** Room entities, DAOs, repositories (Character, Progress, Curriculum,
      Settings) with Flow APIs; in-memory + migration tests.
- [ ] **Domain:** SRS engine (SM-2 + state machine, learning steps per `06`) fully
      unit-tested; use cases for "next new character", "due queue", "submit review".
- [ ] **Stroke engine integration** (`:feature:practice`): renderer + demo + quiz wired to
      the real dataset; `GradingConfig` re-tuned against the full HSK 1 set; grade
      mapping per `05`.
- [ ] **Screens:** Home, Character Detail, Practice, Review, Browse (search + HSK 1 list),
      minimal Settings.
- [ ] **Audio:** TTS wired (zh-CN) with graceful no-engine fallback and the polyphonic
      example-word rule (`01`/`07`).
- [ ] **Theme:** light/dark, semantic colors, rice-grid guide; tracing-guide defaults
      (on for learn, off for review).
- [ ] **Curriculum:** HSK 1 ~174 chars, frequency-ordered, daily cap (default 10),
      unlock gating.
- [ ] Smoke test: a fresh user can learn all HSK 1 characters and review them next day.

**Definition of done:** a new user can learn all ~174 HSK 1 characters with stroke-order
feedback, hear pronunciation, read a definition + one example, and get a correct SRS review
queue the next day — fully offline.

## Phase 3 — Polish & depth

Goal: make it genuinely pleasant and deepen the content.

- [ ] Richer example sentences (multiple per character, curated shortlist).
- [ ] Audio polish: better TTS handling, per-word/per-sentence playback.
- [ ] Progress dashboard (heatmap, retention chart, mastered count over time).
- [ ] Custom decks / favorites ("star" a character) — user-side mirror of `CurriculumEntry`.
- [ ] Frequency-only track (`curriculumId = "freq"`) + HSK 2/3 characters.
- [ ] Outline-clip demo animation (if the thick-median MVP demo isn't good enough).
- [ ] Radical browser + decomposition navigation.
- [ ] Onboarding screen + adaptive icon.
- [ ] Performance pass + robustness (large decks, edge cases).

**Definition of done:** the app feels finished for daily use; content extends beyond HSK 1;
progress is motivating without being manipulative.

## Phase 4 — Publish

Goal: ship to the Play Store as a free, open product with a clear conscience on privacy
and data licensing.

- [ ] License-obligation checklist in `02` fully closed; credits screen ships all
      attributions + license texts.
- [ ] Final `applicationId` (owned namespace), Play listing, screenshots, privacy policy.
- [ ] Privacy-respecting, **opt-in** crash reporting.
- [ ] Backup/restore (export/import a small JSON of progress).
- [ ] Achievements / milestones (honest, non-dark-pattern).
- [ ] Wider curricula (HSK 4–6), traditional-character support (`zh-Hant` data addition —
      see `09`).
- [ ] Optional: cloud sync of progress (accounts optional; layers over existing repository
      interfaces). Optional recorded audio track (if licensing + size allow).

**Definition of done:** live on the Play Store; privacy policy and data attribution are
accurate; a stranger can install it and learn.

## Cross-cutting (always)

- Every phase keeps `:core:domain` pure (no `android.*`) and well-tested; SRS + grading
  are the trust core.
- Every phase respects the constitution: offline-first, minimal permissions, no trackers,
  data transparency, free/open.
- Remaining ⚠ verify flags in `02` (field names, export formats) are resolved during
  Phase 1 ingestion work; license compliance closes before Phase 4 ships.

## Directions deliberately not scheduled (but designed for)

- **iOS** — planned direction *after* the Android MVP validates: convert `:core:domain`
  (and `:core:data` via Room KMP) to Kotlin Multiplatform; UI approach decided then. See
  `09-extension-paths.md`.
- **Japanese / Korean tracks** — concrete data sources and deltas documented in
  `09-extension-paths.md`; not scheduled within these phases.

## Explicitly out of scope

- A custom TTS voice or recorded-audio production pipeline (only revisited in Phase 4, and
  only if it stays cheap).
- User-generated content / social features.
- Grammar curriculum.
