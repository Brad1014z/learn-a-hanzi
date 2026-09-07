# learn-a-hanzi

A native Android app for learning to **write** Chinese characters with correct stroke
order — reinforced with meaning (zh→en), pronunciation (pinyin + TTS), and useful phrases
and sentences, driven by a spaced-repetition review loop — **and shaped as a game**:
mastered characters join your collection, the curriculum unfolds as worlds, and each day
brings a quest and a shareable challenge. Co-built by a father-son team.

> **Status:** M4.1 “First Ten Minutes” implementation and pilot preparation. Social/cloud
> and iOS are frozen. Distribution is blocked on signed Chinese-teacher content, real-stroke
> calibration, teen-pilot gates, approved UI mockups, and a data-license review; see
> [`m4.1-release-gates`](./docs/milestones/m4.1-release-gates.md).

## Build & run

Requirements: any installed JDK to launch Gradle (the build itself is pinned to
JDK 17 via `gradle/gradle-daemon-jvm.properties` and auto-provisioned if missing),
Android SDK (a `local.properties` with `sdk.dir`, or Android Studio).

```bash
./gradlew test                 # engine unit tests incl. the grading golden corpus
./gradlew :app:assembleDebug   # debug APK → app/build/outputs/apk/debug/
```

CI (GitHub Actions) runs the same on every push/PR — the APK artifacts
(`hanzi-prototype-debug-apk`, `hanzi-release-apk`) are only produced when all tests pass,
and the Compose UI specs then run on real API 26 / API 36 emulators.

### Release builds & pilot distribution

```bash
./gradlew :app:assembleRelease  # R8-minified; unsigned unless the signing env vars are set
```

Release signing reads four environment variables — `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Absent, the build still runs and emits
`app-release-unsigned.apk` (so R8 and the keep rules stay covered on every PR); it is
never debug-signed. The keystore is **never committed**. One-time setup:

```bash
keytool -genkeypair -v -keystore release.jks -alias hanzi -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # → repo Settings → Secrets and variables → Actions → KEYSTORE_BASE64
# also add KEYSTORE_PASSWORD, KEY_ALIAS (hanzi), KEY_PASSWORD
```

Pushing a tag `vX.Y.Z` then builds the signed APK and — **only after the emulator jobs
pass** — attaches it to a **GitHub Release**, which is the pilot download page. The job
refuses to publish an unsigned APK. The `applicationId` (`io.github.brad1014z.hanzi`) is
final ([ADR 0001](./docs/adr/0001-application-id-frozen.md)), so pilot installs upgrade in
place rather than forcing a reinstall that would wipe local progress. Tagging does not
bypass the release gates in
[`m4.1-release-gates`](./docs/milestones/m4.1-release-gates.md) — those are human
decisions, not CI steps.

## What this is

Writing is the spine of the app: the user draws each stroke on a native Compose `Canvas`,
and a custom grading engine checks stroke **order, position, and shape** against the target
character. Meaning, pronunciation, and example sentences exist to make each character stick.

- **Platform:** Android · Kotlin · Jetpack Compose · Material 3 — with a pure-Kotlin,
  KMP-ready core so iOS and other languages stay open options
  (see [`09-extension-paths`](./docs/specs/09-extension-paths.md))
- **Storage:** Room (SQLite), **offline-first**
- **Stroke input:** native Canvas, custom grading (no WebView)
- **Audio:** Android `TextToSpeech`
- **Content:** open datasets — make-me-a-hanzi, CC-CEDICT, Unihan, Tatoeba
- **Ambition:** a free, open product on the Play Store — no ads, no trackers

## Read the specs

Everything worth knowing before writing code lives in [`docs/`](./docs/README.md). Suggested
order:

1. [`00-constitution`](./docs/specs/00-constitution.md) — mission, principles, non-goals
2. [`01-tech-stack`](./docs/specs/01-tech-stack.md) — dependencies & rationale
3. [`02-data-sources`](./docs/specs/02-data-sources.md) — datasets, formats, licenses ⚠
4. [`03-data-model`](./docs/specs/03-data-model.md) — Room schema
5. [`04-curriculum`](./docs/specs/04-curriculum.md) — HSK ordering & progression
6. [`05-stroke-engine`](./docs/specs/05-stroke-engine.md) — the grading algorithm ⚠
7. [`06-architecture`](./docs/specs/06-architecture.md) — modules, layers, state
8. [`07-design-system`](./docs/specs/07-design-system.md) — screens & flows
9. [`08-roadmap`](./docs/specs/08-roadmap.md) — phased milestones
10. [`09-extension-paths`](./docs/specs/09-extension-paths.md) — iOS/KMP, Japanese, Korean
11. [`10-play-layer`](./docs/specs/10-play-layer.md) — the game design (XP, collection, worlds, quest, badges)
12. [`11-family-prototype`](./docs/specs/11-family-prototype.md) — the kid co-creator's Phase 0 mini-spec
13. [`12-accounts-social`](./docs/specs/12-accounts-social.md) — optional cloud layer: sign-in, sync, friends challenges

The two ⚠ docs carry the most technical/licensing risk and should be reviewed first.

## Project status

Following [`08-roadmap`](./docs/specs/08-roadmap.md):

- [x] Draft spec set
- [x] Spec review — specs ACCEPTED (2026-07)
- [x] Technical foundation (engine, ingest, Room, Compose, offline audio)
- [x] M4.1 engineering loop (transactional quest, three-plus-two rhythm, social freeze)
- [ ] M4.1 external gates (teacher sign-off, mockup approval, calibration corpus, teen pilot)
- [ ] License/attribution review and distribution approval
- [ ] Social/cloud re-enable blockers
- [ ] iOS/KMP portability work

## License

**No redistribution grant is asserted yet.** Repository files previously disagreed about
source versions and terms. Application-code licensing, dataset compatibility, attribution,
and audio redistribution all require an appropriate license review before any APK or bundled
dataset is distributed. [`m4.1-release-gates`](./docs/milestones/m4.1-release-gates.md)
records the conflicts without inferring legal compatibility.
