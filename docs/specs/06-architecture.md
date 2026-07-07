# 06 — Architecture

> **Status:** ACCEPTED (reviewed 2026-07-05)
> Module layout, layering, dependency rules, state ownership, and the SRS engine.

## Module layout

Multi-module Gradle build. Modules point **inward**: feature modules depend on core; core
never depends on a feature.

```
:app                  -- Application class, Hilt, nav host, theme wiring. Thin.
:core:data            -- Room DB, DAOs, DataStore, repositories (interface + impl).
:core:domain          -- Use cases, SRS engine, stroke-grading math, models (pure Kotlin).
:core:ui              -- Shared Compose theme, design-system tokens, reusable components.
:feature:practice     -- Stroke engine + practice screen (Canvas-heavy).
:feature:review       -- SRS review queue screen.
:feature:browser      -- Character detail, search, radical browser, Home/dashboard.
:data-ingest          -- JVM tool (NOT shipped). Produces the bundled SQLite asset.
```

Phase 4 adds the optional cloud modules, all behind interfaces declared in `:core:domain`
(see `12`; adapted from the closed PR #1 architecture):

```
:core:account         -- AccountRepository impl (Credential Manager + Firebase Auth).
:core:sync            -- SyncRepository/SocialRepository impls (Firestore) + outbox worker.
:feature:social       -- Profile & Friends screen, challenge flows, weekly board.
```

- Feature modules depend only on the **interfaces**; the Firebase impls are single
  swappable modules bound via Hilt. Fakes exist so all social UI tests run offline.
- If no user is signed in, the impls are inert no-ops — the app behaves exactly like the
  signed-out experience (constitution principle 1).

- `:core:domain` is a **plain `kotlin("jvm")` module — the Android Gradle plugin is not
  applied**, so `android.*` imports are impossible by construction. This is the
  constitution's "portable core": converting it to a Kotlin Multiplatform module when iOS
  work starts is mechanical (see `09-extension-paths.md`). Time and coroutine dispatchers
  are injected. The **stroke-grading math** (polyline ops, scoring, the vendored SVG path
  parser) lives here, not in `:feature:practice` — the feature module contributes only
  Canvas rendering and touch capture.
- *Phase 0 note:* the family prototype already applies this rule — its `:engine` module
  is exactly this pure-JVM shape (geometry, parser, grader, quiz state machine, character
  loading) and graduates to `:core:domain` in Phase 1 with its tests.
- `:feature:*` depend on `:core:domain`, `:core:ui`, and `:core:data` (via interfaces).
- `:app` wires implementations (Hilt modules bind `Repository` impls from `:core:data` to
  interfaces declared in `:core:domain`).

## Layering (runtime call flow)

```
UI (Compose)
  └─ reads/observes ViewModel.StateFlow<UiState>
ViewModel
  └─ calls Domain use cases (suspend)
Domain (use cases + SrsEngine)
  └─ calls Repository interfaces
Repository (impl in :core:data)
  └─ Room DAOs / DataStore
```

**Rules:**

- UI never touches Room or repositories directly. Only via ViewModel.
- ViewModel never touches Room directly. Only via use cases / repository interfaces.
- Domain depends only on interfaces, not on Room/Android types (except dispatchers).
- Data flows **up** as `Flow`/values; commands flow **down** as suspend function calls.

## Dependency injection (Hilt)

- `@HiltAndroidApp` on the `Application`.
- `DatabaseModule` provides the Room DB + DAOs.
- `RepositoryModule` (in `:app`, or in `:core:data`) binds `CharacterRepository`,
  `ProgressRepository`, etc. interfaces to their implementations.
- `EngineModule` provides the `SrsEngine` and stroke grader as singletons (they're stateless).
- `DispatchersModule` provides `@IoDispatcher`, `@DefaultDispatcher` qualifiers so domain
  code is dispatcher-agnostic and testable.

## State management

- **One `ViewModel` per screen**, exposing a single `StateFlow<UiState>`.
- `UiState` is an immutable data class; mutations go through `reduce`-style updates inside
  the ViewModel. No `MutableStateFlow` leaks to UI.
- UI effects that must fire once (navigation, show snackbar, play sound) go through a
  `SharedFlow<UiEvent>` / `Channel`, never through `UiState` (which can replay).
- Practice screen holds **transient drawing state** (the current polyline, accepted
  strokes) in the ViewModel too, so it survives configuration changes — but the *raw touch
  stream* lives in the Composable (it's too high-frequency to round-trip through VM state).

## SRS engine (`:core:domain`)

A small, well-tested pure-Kotlin component.

- **Algorithm:** SM-2 (the classic SuperMemo-2 schedule) for its simplicity and adequate
  quality. Inputs: prior `(interval, ease, reps)`, a grade. Outputs: new
  `(interval, ease, reps, dueAt)`.
- **Grade scale (0–5)** as SM-2 defines:
  - 5 — perfect
  - 4 — correct, slight hesitation
  - 3 — correct, with effort
  - 2 — incorrect, but the correct one seemed easy to recall
  - 1 — incorrect, but familiar
  - 0 — complete blackout
  In practice the **drawing practice** maps to a coarse grade — the authoritative
  outcome→grade table lives in `05` (clean → 5, sloppy → 4, hinted → 3, heavy retries →
  2, abandoned → 1; grades < 3 are lapses). The user never sees the 0–5 number; the
  mapping is internal.
- **State machine** on top of SM-2 (the `state` column in `CharacterProgress`):
  `NEW → LEARNING → REVIEW`, with `RELEARNING` on lapse. `LEARNING`/`RELEARNING` use two
  fixed steps — **re-test at the tail of the current session's queue, then 1 day** —
  before graduating to the SM-2 interval schedule. (This is what `07` Flow A's "first
  review" means; there is no same-minute timer.)
- The engine is a **pure function** of `(progress, grade, now)` → new progress. Fully unit-
  tested, including edge cases (lapse chains, ease floor, interval caps).
- **Upgrade path:** FSRS is the modern successor to SM-2. `ReviewLog` already captures the
  full per-review history FSRS trains on, so a later swap is a domain-module change with
  no schema migration. MVP ships SM-2 for simplicity.

## Repositories (interfaces in `:core:domain`)

- `CharacterRepository` — character lookups, stroke paths, words, sentences; queries by
  HSK level / frequency / curriculum order.
- `ProgressRepository` — get/upsert `CharacterProgress`, append `ReviewLog`, query due queue.
- `CurriculumRepository` — what's unlocked, next new characters, daily cap enforcement;
  reads `CurriculumEntry` (see `03`).
- `SettingsRepository` — over DataStore: daily cap, theme, TTS prefs, sound.

Each returns `Flow` for observable data and `suspend` for writes.

Platform services follow the same pattern: **`SpeechService.speak(text, lang)`** is a
pure interface in `:core:domain` (BCP-47 lang), implemented by Android `TextToSpeech`
now and by the pre-generated-audio player in Phase 3 (`01`), so pronunciation is
language- and backend-agnostic by construction.

Phase 4 adds `AccountRepository`, `SocialRepository` (friends, challenges, boards), and
`SyncRepository` (progress backup/restore, outbox) — interfaces in `:core:domain`,
Firebase impls behind them (`12`).

## Offline-first rules (constitution → architecture)

1. **No runtime network dependency.** The app launches and is fully usable with airplane
   mode on, from first run. All content is in the bundled SQLite asset.
2. **First-launch DB setup** copies the asset DB (and runs any reseed logic if a newer
   dataset version is bundled than on disk). This is local I/O only.
3. **No background services** in MVP. Reviews are computed from `dueAt` on foreground.
4. Sync (Phase 4, cloud layer — `12`) layers over these interfaces and must remain optional.

## Navigation

- Compose Navigation (or a thin wrapper) with a single nav host in `:app`.
- Routes are typed (sealed classes / serializable routes), not magic strings, to keep
  navigation refactor-safe.
- Deep links: support a deep link to a character detail (`hanzi://character/你`) for
  shareability later; not required for MVP but cheap to design for.

## Threading

- DB reads/writes: `Dispatchers.IO` (injected qualifier).
- Grading (cheap) and SRS math: `Dispatchers.Default`.
- UI: `Dispatchers.Main`. ViewModels use `viewModelScope`.

## Error handling

- Repository/DAO errors bubble as typed `Result`-like sealed results or exceptions caught
  in the ViewModel, mapped to `UiState.Error` with a user-friendly message + retry action.
- No silent swallows; no generic "something went wrong" without a retry path.

## Testing strategy (architecture-level)

- `:core:domain` — pure unit tests; the SRS engine and stroke grader have the deepest
  coverage. Golden-path stroke corpus (see `05`).
- `:core:data` — Room DAO tests with in-memory DB; repository tests with fake DAOs.
- `:feature:*` — Compose UI tests for the key flows (learn a char, complete a review).
  Fakes for repositories so UI tests don't touch a real DB.
- `:app` — minimal smoke test (app launches, Home renders).

## Open questions

- [ ] Single `:core:ui` vs splitting design-system tokens from reusable components —
      defer to when the component count grows.
- [ ] Compose Navigation vs a typed-routing library (e.g. compose-destinations) — start
      with stock Compose Navigation, reassess if route boilerplate bites.
- [x] ~~Configurable learning steps?~~ — **decided: hardcode the two steps
      (end-of-session, 1 day) for MVP**; expose in Settings later if users ask.
