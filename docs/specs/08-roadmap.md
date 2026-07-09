# 08 — Roadmap

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-05 — family prototype & play
> layer; amended 2026-07-06 — cloud layer inserted as Phase 4, publish renumbered to 5)
> Phased milestones. Each phase has a clear **definition of done** so "are we there?" is
> answerable. Dates are not committed — quality over speed — but the destination is a
> published, free/open product (constitution).

## Phase 0 — Family prototype (stroke engine + game feel)

Goal: validate the single riskiest component — **does grading feel right on a real
device?** — *and* get the father-son build off to a high-feedback start. One app, no
Room, no SRS, no nav; two named workstreams (see `11-family-prototype.md` for the
session-by-session plan):

**Engine workstream (dad + AI):**

- [x] Specs reviewed and marked `ACCEPTED` (2026-07-05, this document set).
- [x] Prototype app skeleton: `:app` (Compose) + `:engine` — a plain `kotlin("jvm")`
      module with no Android plugin, so the engine's purity rule (see `06`) holds by
      construction; it graduates to `:core:domain` in Phase 1. CI (GitHub Actions) runs
      the test suite and builds a debug APK artifact only when tests pass.
- [x] 20 HSK 1 characters as checked-in per-character JSON from hanzi-writer-data
      (same lineage/license as make-me-a-hanzi; zero pipeline work — see `02`), with
      dots, hooks, and crossings (一 人 我 火 心 小 十 中 …).
- [x] Vendored pure-Kotlin SVG-path parser + Y-flip/normalization applied at load
      (with a regression test that 三's strokes run top-to-bottom on screen).
- [x] **Meanings** (added 2026-07-06): a 20-entry dictionary slice (pinyin + definitions
      from make-me-a-hanzi `dictionary.txt`, LGPL — see `02`) shown on grid tiles, in the
      practice header, and on the completion overlay.
- [x] **Verdict sounds** (added 2026-07-06): synthesized placeholder WAVs — accept ding,
      gentle reject tone, completion sparkle — via SoundPool with an on/off toggle
      (`07`). Final sounds are S4's job (`11`).
- [x] **TTS** (added 2026-07-06): `SpeechService(text, lang)` interface in `:engine`
      (pure, with a tested lang→locale mapping); Android `TextToSpeech` impl (zh-CN,
      QUEUE_FLUSH debounce); speaker button on the practice meaning line + auto-speak on
      completion; button hides when no Mandarin voice exists. The one-time
      "install a TTS engine" hint from `01` is deferred to Phase 2.
- [x] **Character Detail intro + phrases + auto-play** (added 2026-07-08): a Character
      Detail screen (grid → detail → practice, spec 07 Flow A) showing 2-3 CC-CEDICT
      phrases per character (auto-ranked, hand-picked, checked in as `phrases.json`) each
      with tap-to-play; the character auto-speaks on intro open and demo start behind an
      "Auto-play audio" toggle (default on).
- [x] Practice canvas: rice-grid guide, demo animation (thick-median variant), quiz mode
      with the full grading pipeline per `05` (capture → accidental-contact filter → RDP →
      resample → length guard → position + direction scores → verdict tiers → hint/undo).
- [ ] `GradingConfig` v0 tuned on-device (finger, at least two screen sizes).
- [x] Golden stroke corpus **started** (synthetic v0): clean / jittered / reversed /
      out-of-order / displaced / truncated attempts derived from real medians, replayed
      through the grader with asserted verdicts. Recorded real-finger strokes join it
      during on-device tuning.

**Game-feel workstream (son + AI, staged in `11`):**

- [ ] S1 "It's alive": a character animates stroke-by-stroke, replay button, his color
      scheme, confetti on completion; working app name v0 (his call).
- [ ] S2 "Trace it": finger ink over a faint character; brush color picker.
- [ ] S3 "The Shelf": collection grid of traced characters, tap to replay.
- [ ] S4 "It judges you": dad's grading verdicts wired to son-designed feedback
      (colors / sound / shake / haptics).
- [ ] S5 "Boss + share": completion celebration, simple XP counter, APK on his phone,
      demo to friends.

**Definition of done:** two or three people can each learn a character they didn't know on
a physical phone, verdicts feel fair — no false rejects on honest attempts, no false
accepts on wrong-order strokes (measured against the corpus, tuned by feel) — **and the
in-house 12-year-old demos it to someone without being asked.**

## Phase 1 — Foundation & data pipeline

Goal: a buildable multi-module repo and a reproducible data pipeline; the prototype's
engine moves into its permanent home.

- [ ] Gradle multi-module skeleton: `:app`, `:core:data`, `:core:domain`, `:core:ui`,
      `:feature:practice`, `:feature:review`, `:feature:browser`, `:data-ingest`.
      `:core:domain` is a plain `kotlin("jvm")` module (see `06`).
- [x] Version catalog (`gradle/libs.versions.toml`) with pinned dependency versions
      (landed with the Phase 0 prototype).
- [x] CI: GitHub Actions — unit tests gate a debug-APK artifact (landed in Phase 0).
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
- [ ] **Screens:** Quest Hub (Home), Character Detail, Practice, Review, Browse (search +
      HSK 1 list), Collection (simple grid + ranks), minimal Settings.
- [ ] **Play layer v1** (per `10`): daily quest frame (warm-up → reviews → new → boss →
      chest), session XP + learner level, collection ranks (silhouette/Bronze/Silver/Gold,
      lapse dimming), world grouping + unlock gating.
- [ ] **Audio:** TTS wired (zh-CN) with graceful no-engine fallback and the polyphonic
      example-word rule (`01`/`07`).
- [ ] **Theme:** light/dark, semantic colors, rice-grid guide; tracing-guide defaults
      (on for learn, off for review); celebration moments (short, skippable).
- [ ] **Curriculum:** HSK 1 ~174 chars, frequency-ordered within hand-curated worlds,
      daily cap (default 10), world unlock gating.
- [ ] Smoke test: a fresh user can learn all HSK 1 characters and review them next day.

**Definition of done:** a new user can learn all ~174 HSK 1 characters with stroke-order
feedback, hear pronunciation, read a definition + one example, and get a correct SRS review
queue the next day — fully offline — inside the quest/collection game frame, with every
rank and unlock truthfully reflecting SRS state.

## Phase 3 — Polish & depth

Goal: make it genuinely pleasant and deepen the content.

- [ ] **Daily challenge + share card** (Flow D in `07`): offline date-derived puzzle,
      spoiler-free emoji result, OS share sheet.
- [ ] **Opt-in arcade** (per `10`): timed modes over mastered (Bronze+) characters only —
      Stroke Sprint, Combo Tower, Speed Write; local personal bests, cosmetic scores, no
      accounts or server.
- [ ] **Collection art pass:** pictographic personality for HSK 1 characters (etymology
      hints as flavor text; art style per son's direction).
- [ ] **Badges** (local, per `10`): mastery/skill/consistency/explorer categories on the
      Collection badge shelf; transparent criteria; earned fully offline.
- [ ] **Pre-generated audio** (per `01`/`12`): ingest generates clips for all curriculum
      characters/words via cloud TTS; `PregenAudioSpeechService` with device-TTS fallback.
- [ ] Richer example sentences (multiple per character, curated shortlist).
- [ ] Audio polish: per-word/per-sentence playback over the `SpeechService` interface.
- [ ] Stats dashboard in Collection (heatmap, retention chart, mastered over time).
- [ ] Custom decks / favorites ("star" a character) — user-side mirror of `CurriculumEntry`.
- [ ] Frequency-only track (`curriculumId = "freq"`) + HSK 2/3 characters (new worlds).
- [ ] Outline-clip demo animation (if the thick-median MVP demo isn't good enough).
- [ ] Radical browser + decomposition navigation.
- [ ] Onboarding screen + adaptive icon.
- [ ] Performance pass + robustness (large decks, edge cases).

**Definition of done:** the app feels finished for daily use; content extends beyond HSK 1;
progress is motivating without being manipulative.

## Phase 4 — Cloud layer (optional accounts, sync, friends)

Goal: the additive online layer per `12-accounts-social.md` — nothing in Phases 0–3
starts depending on it.

- [ ] Google sign-in (Credential Manager + Firebase Auth); generated display name +
      preset avatar; calm entry points (`07` Flow F).
- [ ] Progress sync/backup: outbox + WorkManager drain; merge rules (latest-review wins,
      append-only log union); restore on fresh install.
- [ ] Friends via mutual code/QR; Profile & Friends screen (`07` #8).
- [ ] Challenges: daily duel + set duel + weekly friends board (ceilinged XP); Cloud
      Function bounds validation; preset reactions only.
- [ ] Badge backup (badges stay locally earned).
- [ ] Account deletion (hard purge function); security rules reviewed; fakes so all
      social UI tests run offline.

**Definition of done:** two phones with two accounts can befriend by code, run a duel
end-to-end, and see the weekly board — while a third, signed-out phone loses nothing;
the airplane-mode regression suite passes untouched.

## Phase 5 — Publish

Goal: ship to the Play Store as a free, open product with a clear conscience on privacy
and data licensing.

- [ ] License-obligation checklist in `02` fully closed; credits screen ships all
      attributions + license texts.
- [ ] Final `applicationId` (owned namespace), Play listing, screenshots, privacy policy
      (covering the `12` data practices); COPPA / Play Families posture reviewed.
- [ ] Privacy-respecting, **opt-in** crash reporting.
- [ ] Local backup/restore (export/import a progress JSON) — independent of cloud sync.
- [ ] Wider curricula (HSK 4–6), traditional-character support (`zh-Hant` data addition —
      see `09`).

**Definition of done:** live on the Play Store; privacy policy and data attribution are
accurate; a stranger can install it and learn.

## Cross-cutting (always)

- Every phase keeps `:core:domain` pure (no `android.*`) and well-tested; SRS + grading
  are the trust core.
- Every phase respects the constitution: offline-first, minimal permissions, no trackers,
  data transparency, free/open.
- Every phase respects the play-layer guardrails (`10`): the writing moment is never
  timed in learn/review, game signals stay mastery-truthful, no dark patterns.
- Remaining ⚠ verify flags in `02` (field names, export formats) are resolved during
  Phase 1 ingestion work; license compliance closes before Phase 5 ships.
- The cloud layer (Phase 4) never becomes a dependency of earlier phases: the
  airplane-mode, signed-out experience is a permanent regression target (`00`, `12`).

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
