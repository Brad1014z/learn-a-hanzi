# 06 — Architecture

> **Status:** DRAFT
> Module layout, layering, dependency rules, state ownership, and the SRS engine.

## Module layout

Multi-module Gradle build. Modules point **inward**: feature modules depend on core; core
never depends on a feature.

```
:app                  -- Application class, Hilt, nav host, theme wiring. Thin.
:core:data            -- Room DB, DAOs, DataStore, repositories (interface + impl).
:core:domain          -- Use cases, SRS engine, models (pure Kotlin, Android-light).
:core:ui              -- Shared Compose theme, design-system tokens, reusable components.
:feature:practice     -- Stroke engine + practice screen (Canvas-heavy).
:feature:review       -- SRS review queue screen.
:feature:browser      -- Character detail, search, radical browser, Home/dashboard.
:data-ingest          -- JVM tool (NOT shipped). Produces the bundled SQLite asset.
```

- `:core:domain` has **no Android framework dependencies** (so it unit-tests purely),
  except where it needs time/coroutine dispatchers, which are injected.
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
  In practice the **drawing practice** maps to a coarse grade: clean accept → 5, sloppy
  accept → 4, needed-a-hint → 3, failed-and-retried → 2, total fail → 1. The user never
  sees the 0–5 number; the mapping is internal.
- **State machine** on top of SM-2 (the `state` column in `CharacterProgress`):
  `NEW → LEARNING → REVIEW`, with `RELEARNING` on lapse. `LEARNING`/`RELEARNING` use short
  steps (e.g. 1 day) before graduating to the SM-2 interval schedule.
- The engine is a **pure function** of `(progress, grade, now)` → new progress. Fully unit-
  tested, including edge cases (lapse chains, ease floor, interval caps).

## Repositories (interfaces in `:core:domain`)

- `CharacterRepository` — character lookups, stroke paths, words, sentences; queries by
  HSK level / frequency / curriculum order.
- `ProgressRepository` — get/upsert `CharacterProgress`, append `ReviewLog`, query due queue.
- `CurriculumRepository` — what's unlocked, next new characters, daily cap enforcement.
- `SettingsRepository` — over DataStore: daily cap, theme, TTS prefs, sound.

Each returns `Flow` for observable data and `suspend` for writes.

## Offline-first rules (constitution → architecture)

1. **No runtime network dependency.** The app launches and is fully usable with airplane
   mode on, from first run. All content is in the bundled SQLite asset.
2. **First-launch DB setup** copies the asset DB (and runs any reseed logic if a newer
   dataset version is bundled than on disk). This is local I/O only.
3. **No background services** in MVP. Reviews are computed from `dueAt` on foreground.
4. Any future sync (Phase 3) layers over these interfaces and must remain optional.

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
- [ ] Whether `LEARNING`/`RELEARNING` step counts (e.g. 1 or 2 learning steps before
      graduation) should be configurable. Lean: hardcode for MVP, expose later.

---

## Phase 2 — Arcade & competition (addendum)

> Adds modules and a cloud layer on top of the architecture above. **None of the Phase 1
> rules change**: Arcade runs offline, the cloud is purely additive, and `:core:domain`
> stays pure-Kotlin and framework-free.

### New modules

```
:core:games       -- pure Kotlin scoring/combo/league-domain (the "GameConfig" engine,
                     mirrors how :core:domain holds SrsEngine). Fully unit-tested.
:feature:arcade   -- Arcade hub + game screens + league/leaderboard views. Reuses the
                     stroke grader from :feature:practice's engine module.
```

Optional cloud modules (Phase 2b/2c), all behind interfaces:

```
:core:account     -- AccountRepository interface (sign in/out, handle, delete) in domain;
                     impl behind it.
:core:sync        -- ScoreRepository / LeagueRepository interfaces in domain; Firebase or
                     Supabase impl behind them. WorkManager worker drains the outbox.
```

### Dependency rules (unchanged + reinforced)

- `:feature:arcade` depends on `:core:games`, the stroke grader, `:core:ui`, and the
  **interfaces** from `:core:account` / `:core:sync` — never on Firebase/Supabase types.
- `:core:games` is pure Kotlin (no Android), unit-tested like `SrsEngine`: combo math,
  scoring formula, theoretical-max calculation, league promotion/relegation logic.
- The Firebase/Supabase impl is a single swappable module bound via Hilt; fakes exist so
  Arcade UI tests run fully offline (no emulator, no network).

### Offline-first reinforcement

- **Arcade games run with zero network.** Only XP submission and league fetch are gated on
  auth + connectivity. See `09` (offline behavior) and `10` (outbox).
- The `ScoreRepository` interface has a local-first shape: `submit()` always writes Room +
  the outbox immediately and returns; cloud drain is asynchronous and never blocks the UI.
- If no backend is configured (e.g. unsigned-in user), the impls are no-ops for cloud calls
  and the app behaves identically to local-only — the constitution's #1 principle holds.

### State ownership (additions)

- `ArcadeViewModel` per game screen holds transient game state (score, combo, timer,
  current target char) in `StateFlow`; raw touch input stays in the Composable (same
  pattern as the practice screen).
- `LeagueViewModel` exposes cached standings as `StateFlow`, with an explicit
  "offline / stale" flag rather than a spinner when no network.

### Testing additions

- `:core:games` — deep unit coverage on scoring/combo/league math; property tests on the
  theoretical-max validator.
- `:feature:arcade` — Compose UI tests with `FakeScoreRepository` / `FakeLeagueRepository`;
  no Firebase emulator needed for the UI suite.
- Cloud impl — a thin integration test against the Firebase/Supabase emulator (optional,
  CI-gated).
