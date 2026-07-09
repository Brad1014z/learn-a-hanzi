# 01 — Tech Stack

> **Status:** ACCEPTED (reviewed 2026-07-05)
> Concrete technology choices, with rationale and the boundaries within which each is used.
> Versions are pinned via a Gradle version catalog; exact numbers are set in
> `gradle/libs.versions.toml` at implementation time and kept current.

## Summary

A modern, single-platform native Android app. No cross-platform framework, no WebView for
core learning, no remote backend in the MVP. The domain core is written as **pure Kotlin
(KMP-ready)** so an iOS build later reuses it rather than rewriting it — see
`06-architecture.md` and `09-extension-paths.md`.

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
- **No network persistence layer in MVP.** Sync is a Phase 4 (cloud layer, `12`) concern
  and layers over the same repository interfaces, not replacing them.

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

- **Language-agnostic interface:** all pronunciation goes through a pure
  `SpeechService.speak(text, lang)` interface in `:core:domain` (BCP-47 lang tags — the
  same hedge as the data model), so ja/ko later are new locales, not new designs (`09`).
- **MVP implementation: Android `TextToSpeech`** with `Locale.SIMPLIFIED_CHINESE`
  (zh-CN, Mandarin). The app sets the language per utterance and speaks the underlying
  characters.
- **Quality upgrade (Phase 3): pre-generated audio as data** — the ingest pipeline calls
  a cloud TTS once per curriculum character/word and ships the clips; a
  `PregenAudioSpeechService` plays them, falling back to device TTS for anything
  uncovered. No runtime audio backend (see `12`).
- **Polyphonic characters (多音字):** TTS reads a bare character like 了 or 长 with *one*
  reading, which may contradict the pinyin shown on screen. Where the taught reading is
  ambiguous, the audio button speaks the character inside its example word instead (see
  `07-design-system.md`).
- **Graceful degradation:** if no Mandarin TTS engine is installed, the app shows a one-time
  hint pointing the user to install one, and silently falls back to pinyin-only display.
  Pronunciation is never a hard blocker for a learning session.
- **No bundled or streamed recorded audio in MVP** (constitution non-goal).

## Stroke engine

- Pure Kotlin, no third-party graphics libraries. Path math (Ramer–Douglas–Peucker,
  resampling, nearest-point distance, polyline scoring, SVG path parsing) lives in the
  **pure-Kotlin core module** (`:core:domain`, no `android.*` imports — see
  `06-architecture.md`); `:feature:practice` contributes only Canvas rendering and touch
  capture. Algorithm in `05-stroke-engine.md`.
- SVG path data (`M/L/Q/C/Z`) is parsed by a **vendored ~100-line pure-Kotlin parser** in
  the core module. (androidx's `PathParser` is a `@RestrictTo` API — lint-blocked for
  apps — and a pure parser keeps the core KMP-ready.) The feature module adapts parsed
  geometry to a Compose `Path` for rendering only.

## Build & tooling

- **Gradle (Kotlin DSL)** with a **version catalog** (`gradle/libs.versions.toml`) — single
  source of truth for dependency versions.
- **`applicationId`:** the prototype uses `io.github.brad1014z.hanzi` — the
  `io.github.<username>` convention is a namespace the maintainer verifiably owns and is
  Play-acceptable. It is immutable once uploaded to Play, so the **final** id (keep this
  one, or a custom domain later) is confirmed before the first Play upload (Phase 5).
- **Build variants:** `debug` / `release` only for MVP. Release uses R8/ProGuard with keep
  rules for Room and any reflection (none planned beyond Room's).
- **Versioning:** semantic version `MAJOR.MINOR.PATCH` in `build.gradle.kts`; the dataset
  carries its own version (see `02-data-sources.md`) so we can detect schema/data changes.
- **CI:** GitHub Actions, **live since Phase 0** — unit tests (including the grading
  golden corpus) gate the build; a debug-APK artifact is produced only when the suite
  passes. The ingest tool's determinism check joins in Phase 1.

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

- No RxJava. kotlinx.serialization **is** used app-side for small structured blobs
  (median point arrays, pinyin lists) and for the Phase 0 prototype's checked-in
  per-character JSON; the ingestion *tool* may use whatever it likes since it isn't
  shipped.
- No WebView, no React Native bridge, no Flutter embedding.
- No Google services (Firebase, etc.) **in the MVP builds**. Phase 4 introduces Firebase
  as the optional cloud layer — see below and `12`.

## Phase 4 additions (cloud layer) — NOT in earlier builds

> Recorded here so the stack decision is set; nothing below is compiled into Phase 0–3
> builds (module separation keeps it out). All cloud access goes through interfaces
> (`AccountRepository`, `SocialRepository`, `SyncRepository` — see `06`), so the vendor
> choice stays reversible.

- **Auth:** Google Sign-In via **Credential Manager** → **Firebase Auth**. No
  email/password.
- **Backend: Firebase** — Firestore (offline-capable client) + Cloud Functions
  (challenge bounds-validation, weekly XP aggregation, friend-code exchange, deletion
  purge). Chosen 2026-07-06 over Supabase/Cloudflare for auth simplicity and Android SDK
  maturity; interfaces keep it swappable.
- **Background sync:** WorkManager drains the sync outbox on connectivity with backoff.
- **Audio generation (Phase 3, ingest-side):** a cloud TTS API used by `:data-ingest`
  only — never called from the app; if runtime TTS is ever needed, a Cloudflare Worker
  proxy with edge caching is the designated shape (`12`).

## Open questions

- [ ] Final `applicationId` / package name — an owned namespace, decided before the first
      Play upload (Phase 5).
- [x] ~~`PathParser` vs vendored parser~~ — **decided: vendor a pure-Kotlin SVG-path
      parser** (androidx `PathParser` is `@RestrictTo`; the core must stay free of
      `android.*`).
- [ ] Confirm minSdk 26 doesn't exclude a meaningful share of the audience — check Play
      device stats at publish time (Phase 5).
