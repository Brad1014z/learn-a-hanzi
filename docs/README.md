# Hanzi — Specification

Hanzi is a native Android app for learning to **write** Chinese characters with correct
stroke order, reinforced with meaning (zh→en), pronunciation (pinyin + TTS), and useful
phrases/sentences — all driven by a spaced-repetition review loop, and framed as **a
game**: characters join your collection as you master them, the curriculum unfolds as
worlds, and each day brings a quest and a shareable challenge (see `10-play-layer.md`).
It is co-built by a father-son team (see `11-family-prototype.md`).

This directory is the authoritative spec set. Each document is self-contained and
decision-record-like: it states what we chose, why, and what is explicitly out of scope.

## Stack at a glance

| Concern        | Choice                                          |
|----------------|-------------------------------------------------|
| Language       | Kotlin                                          |
| UI             | Jetpack Compose + Material 3                    |
| Persistence    | Room (SQLite), offline-first                    |
| Stroke input   | Native Compose Canvas, custom grading           |
| Audio          | Android `TextToSpeech` (Mandarin)               |
| DI / Async     | Hilt / Coroutines + Flow                        |
| Portability    | Pure-Kotlin KMP-ready core; Android-only build  |
| Audio          | System TTS behind `SpeechService`; pre-gen clips later |
| Cloud (P4)     | Optional: Firebase sign-in, sync, friends challenges |
| Ambition       | Free/open product (Play Store; open source)     |

## Reading order

Read top to bottom. The first two (`00`, `01`) frame everything; `02` and `05` carry the
most technical risk and should be reviewed carefully.

| #   | Document               | What it answers                                             |
|-----|------------------------|-------------------------------------------------------------|
| 00  | [Constitution](./specs/00-constitution.md)         | Why this exists, principles, non-goals              |
| 01  | [Tech Stack](./specs/01-tech-stack.md)             | Concrete dependencies, versions, rationale          |
| 02  | [Data Sources](./specs/02-data-sources.md)         | Datasets, formats, licenses, ingestion pipeline     |
| 03  | [Data Model](./specs/03-data-model.md)             | Room entities, relationships, indexes               |
| 04  | [Curriculum](./specs/04-curriculum.md)             | HSK ordering, levels, progression rules             |
| 05  | [Stroke Engine](./specs/05-stroke-engine.md)       | Native Canvas grading algorithm (hardest part)       |
| 06  | [Architecture](./specs/06-architecture.md)         | Modules, layers, state, offline-first rules          |
| 07  | [Design System](./specs/07-design-system.md)       | Theme, core screens, key user flows                  |
| 08  | [Roadmap](./specs/08-roadmap.md)                   | Phased milestones, prototype → publish              |
| 09  | [Extension Paths](./specs/09-extension-paths.md)   | iOS/KMP, Japanese, Korean, traditional Chinese       |
| 10  | [Play Layer](./specs/10-play-layer.md)             | Game design: XP, collection, worlds, quest, badges, arcade |
| 11  | [Family Prototype](./specs/11-family-prototype.md) | The 12-year-old co-creator's staged Phase 0 workstream |
| 12  | [Accounts, Sync & Social](./specs/12-accounts-social.md) | Optional cloud layer: Google sign-in, sync, friends challenges (Phase 4) |

## Status legend

Each spec carries a status line at the top:

- `DRAFT` — proposed, awaiting review
- `ACCEPTED` — agreed, governs implementation
- `SUPERSEDED` — replaced; pointer to the replacement

## Verification flags

Items the spec asserts but that must be confirmed against upstream sources before the
related code ships are marked **⚠ verify**. Licenses and repository facts were verified by
web research on **2026-07-05** (see `02-data-sources.md`); the remaining flags are
ingest-time checks (exact field names, export formats) that the ingest tool confirms
mechanically and fails loudly on.
