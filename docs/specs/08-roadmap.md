# 08 — Roadmap

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-05 — family prototype & play
> layer; amended 2026-07-06 — cloud layer inserted; **rewritten 2026-07-09 — Phases 1–4
> re-sliced into milestone lanes M1–M5** so every milestone ends in something visible on
> a real phone; the cloud slice moves ahead of polish; LLM-generated sentences and
> pre-generated TTS audio join the content milestone. Decisions from the roadmap
> grilling session of 2026-07-08/09.)
> Milestones, each with a **definition of done phrased as a demo** so "are we there?" is
> answerable by playing the build. Dates are not committed — quality over speed — but the
> destination is a published, free/open product (constitution).

## How this roadmap works (the 2026-07-09 re-slice)

Phase 0 proved more than the grading engine: the sessions that ended with something
touchable were the ones that kept both builders coming back. The old Phase 1
("multi-module skeleton + data pipeline + an app that launches to a blank screen")
broke that rule — weeks of plumbing with nothing to demo. So the roadmap is re-sliced
around four rules:

1. **Every milestone ends in a feature someone can see on a phone.** Its definition of
   done is written as the demo itself.
2. **Plumbing rides inside feature milestones.** The multi-module split, Hilt,
   navigation — each lands when (and only when) a milestone needs it, never as its own
   phase.
3. **Two lanes, one product.** Dad's milestone lane (M1–M5 below) and the son's
   game-feel lane (S1–S5, unchanged — see `11`) run interleaved and merge at weekly
   family demo days. Every dad milestone reserves son-owned surfaces
   (`TODO(son, …)` markers in code).
4. **Mockup-first design.** Any new screen starts as 2–3 Claude-generated interactive
   HTML mockups; the son picks and tweaks as art director; the winner is built in
   Compose (see `07`).

Legacy phase numbers map as: Phases 1+2 → M1–M3; Phase 3 → M5 (pre-generated audio and
richer sentences moved up into M2); Phase 4 → M4; Phase 5 → Publish (unchanged). Specs
whose schedule materially changed (`01`, `12`) have been updated; incidental "Phase N"
references elsewhere read through this mapping.

## Phase 0 — Family prototype (stroke engine + game feel)

Goal: validate the single riskiest component — **does grading feel right on a real
device?** — *and* get the father-son build off to a high-feedback start. One app, no
Room, no SRS, no nav; two named workstreams (see `11-family-prototype.md` for the
session-by-session plan):

**Engine workstream (dad + AI):**

- [x] Specs reviewed and marked `ACCEPTED` (2026-07-05, this document set).
- [x] Prototype app skeleton: `:app` (Compose) + `:engine` — a plain `kotlin("jvm")`
      module with no Android plugin, so the engine's purity rule (see `06`) holds by
      construction; it graduates to `:core:domain` when the module split earns its keep
      (see M2). CI (GitHub Actions) runs the test suite and builds a debug APK artifact
      only when tests pass.
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
      "install a TTS engine" hint from `01` is deferred until pre-generated audio (M2)
      makes it mostly moot.
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

**Where the open items live now (2026-07-09):** `GradingConfig` on-device tuning is a
standing engine task that continues alongside M1 (it gates nothing downstream); the
game-feel sessions S1–S5 are the son's lane and run interleaved with the milestones
below.

## M1 — "It remembers you" (persistence)

*(Milestone names are placeholders — naming is the son's job, `11`.)*

Goal: the prototype stops forgetting. User tables land now, shaped exactly per `03`, so
the SRS milestone later (M3) is an engine change, not a schema migration.

- [ ] Room in `:app`: user tables `CharacterProgress`, `ReviewLog`, `Meta` exactly per
      `03` (SRS columns present with sensible pre-SRS defaults); schema JSON checked in
      from v1 so future migrations are testable. (Room DAO/migration test harness —
      Robolectric or instrumented — joins in M3 with the SRS work; the M1 write path is
      covered through the pure repository contract.)
- [ ] `ProgressRepository` interface in `:engine` (pure: `Flow` reads, `suspend`
      writes), mirroring the existing `SpeechService` interface-in-engine pattern;
      Room-backed implementation in `:app`.
- [ ] Practice completion records a `ReviewLog` row and upserts `CharacterProgress`
      using the outcome→grade table in `05` (already implemented as
      `QuizState.toGrade()`); pre-SRS scheduling fields hold documented placeholder
      semantics until M3's SM-2 takes over.
- [ ] Settings survive restarts: DataStore (Preferences) for the auto-play-audio and
      sound toggles.
- [ ] The grid shows per-character progress (practiced vs. not, at minimum) — the seed
      of the son's S3 "Shelf"; the visual treatment stays his (`TODO(son, S3)`).

**Definition of done (the demo):** practice 火 to completion, force-kill the app,
reopen — the shelf still shows it learned, and the toggles are how you left them.

## M2 — "The world gets big" (content pipeline: ~174 characters, real sentences, one good voice)

Goal: from 20 checked-in characters to the full HSK 1 world, each with a kid-friendly
example sentence and consistent, good pronunciation — still fully offline.

- [x] `:data-ingest` tool per `02` (landed 2026-07-10): reads make-me-a-hanzi (pinned
      commit), CC-CEDICT, Unihan 16, Tatoeba cmn, and the pinned HSK 1 list; verifies
      actual field names against `data/sources.lock` (the ⚠ verify flags **closed** —
      all 178 HSK 1 characters have complete data); applies + validates the Y-flip
      normalization via the engine's own parser; computes Tatoeba-derived frequency
      ranks; emits `CurriculumEntry` rows with hand-curated world tags (`10`,
      `data/pinned/worlds-hsk1.tsv`); **hard-fails** on incomplete curriculum data;
      produces a byte-deterministic `hanzi_v1.sqlite` whose schema + identity hash come
      from the KSP-exported Room schema; emits `data/ingest-report.md` with the
      attribution manifest.
- [x] **LLM-generated example sentences** (landed 2026-07-10 — see `02`): one
      vocabulary-constrained, all-ages sentence per HSK 1 character (178/178) with
      tone-marked pinyin + gloss; model + prompt pinned
      (`data-ingest/prompts/sentence-generation.md`); reviewed in PR
      (`data/pinned/sentences-hsk1.json`); provenance `source="llm"` +
      `forCharacter`; ingest hard-fails on off-list characters.
- [x] **Pre-generated TTS tooling** (moved up from old Phase 3 — see `01`/`02`):
      `PregenAudioSpeechService` (bundled clips with device-TTS fallback) +
      `:data-ingest:generateAudio` (Google Cloud TTS, `GOOGLE_TTS_API_KEY`).
- [x] **TTS clips generated + bundled** (2026-07-12): 779 clips — every character,
      word, and sentence — synthesized with `cmn-CN-Chirp3-HD-Leda` at 0.85 rate
      (~6.3 MB of assets, APK 12 → 17 MB); voice + API recorded in the manifest.
      Every phone now gets the same clear Mandarin voice offline; device TTS remains
      the fallback for uncovered text.
- [x] Content tables per `03` join M1's user tables (schema v2); fresh installs get the
      dataset via `createFromAsset`, existing installs via the `DatasetSeeder` reseed
      that preserves user tables; `Meta.datasetVersion` readable.
- [x] Plumbing that rode along: `:data-ingest` module added; `:engine` stayed `:engine`
      (the `:core:domain` graduation wasn't needed yet — it reuses cleanly as-is).

**Definition of done (the demo):** airplane mode on, open the app on any phone: ~174
characters grouped into worlds, every one speaks with the same clear voice and shows a
sentence a kid actually understands. `./gradlew :data-ingest:run` regenerates the
bundled asset deterministically.

## M3 — "The game begins" (SRS + daily quest + XP)

Goal: the full learn → practice → review loop inside the play-layer frame (`10`) — the
old MVP definition, reached with content and audio already in place.

- [x] **Data layer** (landed 2026-07-12): due-queue / introduced-today / days-played
      queries; XP in `Meta`; daily cap in DataStore; session tags distinguish guided
      quest steps from cap-exempt browse practice (`04`). (Room DAO/migration test
      harness still owed — carried to M4 alongside the sync work.)
- [x] **Domain:** `SrsEngine` (SM-2 + state machine + learning steps per `06`, fully
      unit-tested), `QuestBuilder`/`QuestSession` (warm-up → reviews → new → boss →
      chest with tail re-tests), `Ranks`, `XpConfig`/`Levels` — all pure, all tested.
- [x] **Recall mode** on the practice canvas: reviews/re-tests/boss start straight in
      the quiz — no demo reveal, tracing guide off (boss: guide locked off).
      (`GradingConfig` re-tune against the full set remains a standing tuning task.)
- [x] **Daily quest frame** per `04`/`10`: quest player + chest (XP tally + rank-up
      replay; celebration art is the co-designer's, `TODO(son, S5)`).
- [x] **Collection ranks** with lapse dimming + world unlock gating (80% Bronze+;
      locked worlds stay browsable — gating applies to the guided track only).
- [x] **Screens**: Quest Hub home (quest card, XP/level, days played, world strip),
      Collection upgrade, minimal Settings (cap, toggles, reset). Browse search is
      deferred to M5 with the radical browser (the worlds grid covers discovery).
- [x] Smoke test: on-emulator quest end-to-end; next-day due queue verified by SRS
      unit tests + device-clock check.

**Definition of done (the demo):** come back tomorrow — the app greets you with a quest
built from *your* due queue; finish it, open the chest, watch a character rank up — and
every rank and unlock is truthful to SRS state, fully offline.

## M4 — "Family board" (login, sync, friends — the cloud slice, per `12`)

Goal: the optional cloud layer, **pulled ahead of polish** (decision 2026-07-09): a
weekly family leaderboard is this team's strongest "we built a real product" moment,
and the demo-day friends are the natural first beta cohort (`12` open question —
answered: yes). The constitution is unchanged — everything keeps working offline and
signed out, forever.

- [x] Google sign-in (Credential Manager + Firebase Auth, landed 2026-07-12);
      generated display name + preset avatar + rotatable friend code; the cloud layer
      activates only when `app/google-services.json` exists — CI and fresh checkouts
      build the offline fakes ("preview mode", clearly labeled).
- [x] Progress sync/backup: ReviewLog uuid (schema v3) + SyncOutbox drained by
      WorkManager on connectivity; merge rules per `12` as pure tested functions
      (latest-review wins, log union, XP never decreases); restore-on-sign-in.
- [x] Friends via mutual code (share-sheet text; QR is an M4.5 nicety); Family screen
      (`07` #8): my card, add-by-code, confirm/remove, weekly board.
- [x] Weekly friends XP board — ceilinged per session, client-written, rules-bounded.
      *(M4.5: duels/challenges, preset reactions, Cloud Function validation + weekly
      aggregation, account-deletion purge — needs the Blaze plan; see `12`.)*
- [x] Firestore security rules checked in (`firebase/firestore.rules`): own-subtree
      writes, edge-gated reads, no enumeration; console checklist in
      `firebase/README.md`.
- [x] Offline fakes power every social surface with no Firebase project; the
      signed-out, airplane-mode experience is untouched (INTERNET is now the app's
      single permission, used only by the optional layer).

**Definition of done (the demo):** dad's and the sons' phones befriend by QR and share
one weekly board; a demo-day friend joins in under a minute; a third, signed-out phone
loses nothing.

## M5 — Polish & depth (sliced into demo-sized bites when we get there)

The remains of old Phase 3, minus what M2 already delivered (pre-generated audio,
richer sentences). Each bullet becomes its own mini-milestone with a demo when
scheduled:

- [ ] Daily challenge + spoiler-free share card (`07` Flow D, `10`).
- [ ] Opt-in arcade over mastered (Bronze+) characters only — Stroke Sprint, Combo
      Tower, Speed Write; local personal bests, cosmetic scores (`10`).
- [ ] Badges (local, transparent criteria, `10`) — backed up via M4's sync path.
- [ ] Collection art pass: pictographic personality for HSK 1 (etymology hints as
      flavor text; art style per son's direction).
- [ ] Stats dashboard in Collection (heatmap, retention chart, mastered over time).
- [ ] Custom decks / favorites; frequency-only track (`curriculumId = "freq"`);
      HSK 2/3 characters (new worlds).
- [ ] Radical browser + decomposition navigation; onboarding screen + adaptive icon.
- [ ] Outline-clip demo animation (if the thick-median demo isn't good enough);
      performance pass + robustness (large decks, edge cases).

**Definition of done:** the app feels finished for daily use; content extends beyond
HSK 1; progress is motivating without being manipulative.

## Publish (unchanged — the old Phase 5)

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

- Every milestone keeps `:core:domain` (today `:engine`) pure (no `android.*`) and
  well-tested; SRS + grading are the trust core.
- Every milestone respects the constitution: offline-first, minimal permissions, no
  trackers, data transparency, free/open.
- Every milestone respects the play-layer guardrails (`10`): the writing moment is never
  timed in learn/review, game signals stay mastery-truthful, no dark patterns.
- Remaining ⚠ verify flags in `02` (field names, export formats) are resolved during
  M2 ingestion work; license compliance closes before Publish ships.
- The cloud layer (M4) never becomes a dependency of other milestones: the
  airplane-mode, signed-out experience is a permanent regression target (`00`, `12`).
- Every feature change ships with matching spec-doc revisions in the same PR — the
  specs stay current or they stop being specs.

## Directions deliberately not scheduled (but designed for)

- **iOS** — planned direction *after* the Android core loop (M3) validates: convert
  `:core:domain` (and the data layer via Room KMP) to Kotlin Multiplatform; UI approach
  decided then. See `09-extension-paths.md`.
- **Japanese / Korean tracks** — concrete data sources and deltas documented in
  `09-extension-paths.md`; not scheduled within these milestones.
- **Runtime LLM features** (e.g. a personalized sentence playground) — the ingest-time
  LLM path (M2) is the only sanctioned one for now; a runtime variant would need a
  key-hiding proxy and a constitutional amendment, and is documented as a possible
  extension only.

## Explicitly out of scope

- A custom TTS voice or recorded-audio production pipeline. Pre-generated **cloud-TTS
  clips shipped as data** are the sanctioned quality path (M2, per `01`/`02`); licensing
  or recording human audio stays out.
- User-generated content / open social (the bounded friends layer in `12` is the
  ceiling).
- Grammar curriculum.
