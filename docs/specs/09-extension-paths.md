# 09 — Extension Paths

> **Status:** ACCEPTED (reviewed 2026-07-05)
> Where the product can go after the Android + Simplified-Chinese MVP, and exactly what
> the MVP does (and deliberately does not do) to keep each path cheap. Nothing in this
> document is scheduled; see `08-roadmap.md` for what is. Facts here (licenses, dataset
> shapes) were verified by web research on 2026-07-05.

## The two MVP hedges (already in the specs)

Everything in this document leans on just two decisions the MVP already makes:

1. **Portable core** (`00`, `06`): SRS, stroke-grading math, and the SVG path parser are
   pure Kotlin in `:core:domain`, a plain `kotlin("jvm")` module with no Android plugin.
2. **Language-tagged content + curriculum table** (`03`): content rows carry `lang`
   (BCP-47), and curriculum membership is a `CurriculumEntry` table, not a
   Chinese-specific column.

Everything else below is *future work described*, not present abstraction.

## iOS

**Strategy: Kotlin Multiplatform conversion of the core, after the Android MVP validates.**

- **What converts:** `:core:domain` (SRS engine, grading math, use cases) — mechanical,
  since it's already Android-free. `:core:data` follows via **Room KMP** (stable since
  Room 2.7; Room 3.0 is KMP-first, supporting Android/iOS/desktop) and DataStore KMP.
- **What doesn't:** UI. Compose Multiplatform on iOS vs SwiftUI-over-shared-core is
  decided when iOS work actually starts, not now. The stroke *renderer* and touch capture
  are per-platform either way (they're deliberately thin — see `05`).
- **Platform services** (TTS, haptics): small `expect`/`actual` interfaces; iOS has
  `AVSpeechSynthesizer` for Mandarin/Japanese/Korean.
- **The discipline that keeps this cheap** (enforced now): no `android.*` in the core, no
  Android-plugin modules below the feature layer, dispatchers injected, kotlinx-only
  serialization in shared code.

## Japanese (kanji + kana) — the concrete next language

Japanese is a near-drop-in for the entire stack. Per-source mapping:

| Need | Chinese (MVP) | Japanese analog | License |
|------|---------------|-----------------|---------|
| Stroke geometry + order | make-me-a-hanzi | **KanjiVG** (kanji **and kana**) | CC BY-SA 3.0 |
| Readings + meanings | mmah `dictionary.txt` | **KANJIDIC2** | CC BY-SA (EDRDG) |
| Words | CC-CEDICT | **JMdict** | CC BY-SA (EDRDG) |
| Sentences | Tatoeba `cmn` | **Tatoeba `jpn`** (same files, same pipeline) | per-sentence CC |
| Curriculum | HSK levels | **jōyō grades** and/or **JLPT levels** | public lists |
| TTS | Android TTS zh-CN | Android TTS ja-JP | system |

Engine and pipeline implications:

- **The grading engine needs no changes.** It compares polylines to median polylines;
  it never knew the glyphs were Chinese.
- **KanjiVG paths are centerlines, not outlines** (unlike make-me-a-hanzi's filled
  outlines). So: medians come *directly* from the path data (a simplification), and the
  renderer needs a **thick round-cap stroke variant** instead of filled outlines — which
  is exactly the MVP's fallback demo style (`05`), so the variant likely already exists.
- **Ingest:** a new source adapter emitting the same normalized entities with
  `lang = "ja"` and `CurriculumEntry(curriculumId = "jlpt" | "joyo", …)`.
- **Keying (the one real migration):** many codepoints exist in both languages (学 is
  zh *and* ja) but with different readings/definitions/stroke conventions, so
  `Character(character)` as a single-column PK collides. Resolution options — composite
  PK `(character, lang)` or a prefixed key (`"ja:学"`) — decided at implementation, as a
  normal Room migration. User progress migrates by mapping existing rows to `zh-Hans`.
- **Readings model:** kanji have on/kun readings with usage context — richer than a
  pinyin array. The `pinyin` column generalizes to a per-language `readings` JSON blob at
  that point; the MVP deliberately does **not** pre-abstract this (`03`).
- **Kana track:** KanjiVG covers hiragana/katakana, so a "learn the kana" starter
  curriculum is nearly free and is the natural first Japanese offering.

## Korean (hangul) — furthest out, structurally different

An honest reality check rather than a plan:

- **Hangul is compositional.** Syllable blocks (11,172 possible) are assembled from ~40
  jamo by regular rules. Nobody ships per-syllable stroke data; the meaningful product
  teaches **jamo stroke order + block composition + sound**, which is a different
  curriculum and card model (compose-a-block, not reproduce-a-glyph).
- **What transfers:** the drawing canvas, per-stroke grading (jamo are just small stroke
  sets), SRS, Tatoeba `kor` sentences, Android TTS ko-KR.
- **What doesn't exist:** a KanjiVG-equivalent stroke dataset for jamo/blocks — stroke
  geometry would likely need authoring (~40 jamo is tractable by hand) rather than
  ingesting.
- **Positioning:** hangul is learnable in days (it's an alphabet); the app's writing-first
  value proposition is thinner here. Korean makes sense as a small "learn hangul in a
  week" module, not a full HSK-style track. Decide *whether*, not just how, when the time
  comes.

## Traditional Chinese (zh-Hant)

Not a schema change — a **data addition**: make-me-a-hanzi and CC-CEDICT both carry
traditional forms, so a `zh-Hant` content set plus a curriculum (e.g. TOCFL) slots into
the existing `lang` + `CurriculumEntry` mechanics. The same codepoint-collision note as
Japanese applies (many characters are shared between Hans/Hant sets). Scheduled loosely
under Phase 4 (`08`).

## Branding note

"learn-a-hanzi" / "Hanzi" is Simplified-Chinese-specific naming. If Japanese/Korean ever
ships, the product name, app icon, and store listing need a script-neutral umbrella (or
per-language apps). Pure product decision; no code impact; flagged so the eventual
`applicationId` choice (`01`) at least doesn't bake "hanzi" into a namespace we regret.

## What would trigger each path

- **iOS:** Android MVP retention is good and a meaningful share of interest comes from
  iOS users.
- **Japanese:** the Chinese loop is validated and content pipeline is stable — Japanese is
  then mostly ingest work plus the keying migration.
- **Korean:** only with a deliberate product decision that a short hangul module is worth
  its own pedagogy.
