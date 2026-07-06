# learn-a-hanzi

A native Android app for learning to **write** Chinese characters with correct stroke
order — reinforced with meaning (zh→en), pronunciation (pinyin + TTS), and useful phrases
and sentences, driven by a spaced-repetition review loop — **and shaped as a game**:
mastered characters join your collection, the curriculum unfolds as worlds, and each day
brings a quest and a shareable challenge. Co-built by a father-son team.

> **Status:** Phase 0 in progress — the family prototype builds and its engine test
> suite passes; next up is on-device grading tuning and the co-designer's game-feel
> sessions ([`11-family-prototype`](./docs/specs/11-family-prototype.md)).

## Build & run

Requirements: JDK 17+, Android SDK (a `local.properties` with `sdk.dir`, or Android Studio).

```bash
./gradlew test                 # engine unit tests incl. the grading golden corpus
./gradlew :app:assembleDebug   # debug APK → app/build/outputs/apk/debug/
```

CI (GitHub Actions) runs the same on every push/PR — the APK artifact
(`hanzi-prototype-debug-apk`) is only produced when all tests pass.

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
11. [`10-play-layer`](./docs/specs/10-play-layer.md) — the game design (XP, collection, worlds, quest)
12. [`11-family-prototype`](./docs/specs/11-family-prototype.md) — the kid co-creator's Phase 0 mini-spec

The two ⚠ docs carry the most technical/licensing risk and should be reviewed first.

## Project status

Following [`08-roadmap`](./docs/specs/08-roadmap.md):

- [x] Draft spec set
- [x] Spec review — specs ACCEPTED (2026-07)
- [ ] Phase 0 — family prototype (grading engine + game feel, on-device)
- [ ] Phase 1 — foundation (module skeleton, data-ingest pipeline, CI)
- [ ] Phase 2 — MVP (HSK 1 learn → practice → review loop, offline)
- [ ] Phase 3 — polish & depth
- [ ] Phase 4 — publish (Play Store)

## License

Application code: intended to be released under a permissive license (MIT or Apache-2.0;
see `LICENSE` once chosen).

Learning content is derived from open datasets under their own licenses — see
[`02-data-sources`](./docs/specs/02-data-sources.md) for the full attribution list
(Arphic PL + LGPLv3 for make-me-a-hanzi's two files, CC BY-SA 4.0 for CC-CEDICT,
per-sentence CC for Tatoeba — verified 2026-07). The compliance checklist in `02` closes
before the first public release.
