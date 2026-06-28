# 01 — Tech Stack

> **Status:** DRAFT
> Concrete technology choices, with rationale and the boundaries within which each is used.
> Versions are pinned via a Gradle version catalog; exact numbers are set in
> `gradle/libs.versions.toml` at implementation time and kept current.

## Summary

A modern, single-platform native Android app. No cross-platform framework, no WebView for
core learning, no remote backend in the MVP.

## Language & runtime

| Choice | Decision | Rationale |
|--------|----------|-----------|
| Language | **Kotlin** (latest stable) | First-class Android tooling, concise, coroutine-native. |
| minSdk | **26** (Android 8.0) | Covers ~95%+ of devices; gives modern APIs (adaptive icons, downloadable fonts, java.time) without backport pain. |
| targetSdk / compileSdk | latest stable | Stay current with Play requirements and platform behavior. |

Java 17+ toolchain via the Android Gradle Plugin.

## UI

- **Jetpack Compose** for 100% of UI. No XML layouts, no `View` interop except where a
  platform component truly requires it (none anticipated in MVP).
- **Material 3** component library and theming. Dynamic color (Android 12+) supported but
  optional; a tuned default palette ships for older devices and for brand consistency.
- **Compose Canvas** (`Canvas` / `DrawScope`) for all stroke rendering and drawing input.
  This is the heart of the app — see `05-stroke-engine.md`.

## Persistence

- **Room** over SQLite. All learning data (characters, stroke paths, words, sentences,
  progress, review log) lives in a local DB. See `03-data-model.md`.
- **Bundled asset → pre-populated DB.** The dataset is built offline by the ingestion tool
  (`02-data-sources.md`) and shipped as a SQLite asset that Room copies on first launch.
  After that, only user-generated rows (progress, logs) mutate.
- **DataStore (Preferences)** for small settings (daily new-char cap, theme, TTS engine
  choice, sound on/off). No settings in SharedPreferences.
- **No network persistence layer in MVP.** Sync is a Phase 3 concern and would layer over
  the same repository interfaces, not replace them.

## Dependency injection

- **Hilt**. One `@HiltAndroidApp` application; `@HiltViewModel` for all screens;
  `@Module`s for DB, DataStore, and engine bindings. Hilt over Koin because it is
  compile-time checked and the de-facto Android standard.

## Asynchrony & state

- **Coroutines + Flow** exclusively. No RxJava.
- **State ownership:** one `ViewModel` per screen holds a `StateFlow<UiState>` as the single
  source of truth. UI is a pure function of state. See `06-architecture.md`.
- **Long-running work** (DB import on first launch, review-log writes) uses structured
  concurrency scoped to the relevant component.

## Audio

- **Android `TextToSpeech`** with `LANG_CHINESE` (Mandarin). The app sets the language per
  utterance and speaks the pinyin's underlying characters.
- **Graceful degradation:** if no Mandarin TTS engine is installed, the app shows a one-time
  hint pointing the user to install one, and silently falls back to pinyin-only display.
  Pronunciation is never a hard blocker for a learning session.
- **No bundled or streamed recorded audio in MVP** (constitution non-goal).

## Stroke engine

- Pure Kotlin, no third-party graphics libraries. Path math (Ramer–Douglas–Peucker,
  resampling, nearest-point distance, SVG path parsing) implemented in `:feature:practice`.
  Rationale and algorithm in `05-stroke-engine.md`.
- SVG path data from make-me-a-hanzi is parsed by a small, dependency-light SVG-path-to
  `Path` converter (Android `android.graphics.Path` + `PathParser`, available in Compose).

## Build & tooling

- **Gradle (Kotlin DSL)** with a **version catalog** (`gradle/libs.versions.toml`) — single
  source of truth for dependency versions.
- **`applicationId`:** `dev.brad.hanzi` (final TBD).
- **Build variants:** `debug` / `release` only for MVP. Release uses R8/ProGuard with keep
  rules for Room and any reflection (none planned beyond Room's).
- **Versioning:** semantic version `MAJOR.MINOR.PATCH` in `build.gradle.kts`; the dataset
  carries its own version (see `02-data-sources.md`) so we can detect schema/data changes.

## Testing

| Layer | Tooling |
|-------|---------|
| Pure logic (SRS, path math, grading) | JUnit + kotlinx-coroutines-test; target high coverage here — these are where bugs hide. |
| Flow / StateFlow | Turbine. |
| Repository / Room | Robolectric or instrumented Room tests with an in-memory DB; migration tests via `MigrationTestHelper`. |
| Compose UI | `createComposeRule`, testing user flows not pixel snapshots. |
| Snapshot/visual (optional, later) | Not in MVP. |

The stroke grading algorithm gets its own golden-path test set (a handful of recorded
user strokes against known characters) so tuning changes are measurable. See `05`.

## Analytics & crash reporting

- **MVP:** none. Constitution says no trackers.
- **Later (if published):** an opt-in, privacy-respecting crash reporter (e.g. self-hosted
  or a no-analytics crash tool) — decided at publish time, not now.

## Dependencies we explicitly avoid

- No RxJava, no Kotlinx Serialization if we can use hand-rolled parsing of the small JSON
  lines (kotlinx.serialization is acceptable if ingestion in-app needs it; the ingestion
  *tool* may use whatever it likes since it isn't shipped).
- No WebView, no React Native bridge, no Flutter embedding.
- No Google services (Firebase, etc.) in MVP.

## Open questions to resolve at Phase 0

- [ ] Final `applicationId` / package name.
- [ ] Whether to use `PathParser` from androidx or vendor a tiny SVG-path parser (decide
      when we see real path strings from the dataset).
- [ ] Confirm minSdk 26 doesn't exclude a meaningful share of the intended audience
      (portfolio → likely fine).
