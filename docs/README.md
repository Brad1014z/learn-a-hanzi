# Hanzi — Specification

Hanzi is a native Android app for learning to **write** Chinese characters with correct
stroke order, reinforced with meaning (zh→en), pronunciation (pinyin + TTS), and useful
phrases/sentences — all driven by a spaced-repetition review loop.

This directory is the authoritative spec set. Each document is self-contained and
decision-record-like: it states what we chose, why, and what is explicitly out of scope.

## Stack at a glance

| Concern        | Choice                                   |
|----------------|------------------------------------------|
| Language       | Kotlin                                   |
| UI             | Jetpack Compose + Material 3             |
| Persistence    | Room (SQLite), offline-first             |
| Stroke input   | Native Compose Canvas, custom grading    |
| Audio          | Android `TextToSpeech` (Mandarin)        |
| DI / Async     | Hilt / Coroutines + Flow                 |
| Ambition       | Personal / portfolio (publishable later) |

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
| 08  | [Roadmap](./specs/08-roadmap.md)                   | Phased milestones, MVP → v2                         |

## Status legend

Each spec carries a status line at the top:

- `DRAFT` — proposed, awaiting review
- `ACCEPTED` — agreed, governs implementation
- `SUPERSEDED` — replaced; pointer to the replacement

## Verification flags

Items the spec asserts but that must be confirmed against upstream sources before the
related code ships are marked **⚠ verify**. They are concentrated in `02-data-sources.md`
(license terms, exact field names) because web research was unavailable when these specs
were drafted.
