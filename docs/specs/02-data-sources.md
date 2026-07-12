# 02 — Data Sources

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-09 — LLM-generated example
> sentences and pre-generated TTS clips join the pipeline, per the constitution's
> LLM-assisted-content amendment; scheduled as milestone M2 in `08`)
> This is one of the two riskiest specs. Licenses and repository facts were **verified by
> web research on 2026-07-05**; remaining **⚠ verify** flags are ingest-time checks (exact
> field names, export formats) that the ingest tool confirms mechanically and fails loudly
> on. The shape of the data is stable, but field names change over time — confirm, don't
> assume.

## Source inventory

| Source | Provides | License (verified 2026-07) | Used for |
|--------|----------|----------------------------|----------|
| [make-me-a-hanzi](https://github.com/skishore/makemeahanzi) `graphics.txt` | stroke SVG paths + medians | **Arphic Public License** | stroke rendering + grading |
| [make-me-a-hanzi](https://github.com/skishore/makemeahanzi) `dictionary.txt` | per-char dictionary | **LGPL v3+** (derives from Unihan + CJKlib) | definitions, decomposition, radical |
| [CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) | word list (trad/simpl/pinyin/english) | **CC BY-SA 4.0** | multi-char words, definition cross-check |
| [hanzi-writer-data](https://github.com/chanind/hanzi-writer-data) | derived JSON per character | same lineage (Arphic) | **Phase 0 prototype source** + ingest sanity check |
| [Unihan database](https://www.unicode.org/charts/unihan.html) | stroke counts, radicals, readings | Unicode license (permissive, attribution) | cross-checks (`kTotalStrokes`, `kRSUnicode`) |
| [Tatoeba](https://tatoeba.org/en/downloads) | sentences + translation links | **CC-BY 2.0 FR** (mostly; per-sentence — record it) | fallback example sentences; **character frequency ranks** |
| Claude API (LLM, **ingest-time only**) | generated example sentences | our own generated content; provenance recorded (see below) | primary example sentences: vocab-constrained, kid-friendly |

> The two make-me-a-hanzi files carry **different licenses** (per the repo's `COPYING`):
> the graphics are Arphic PL (copyleft for the font-derived data), the dictionary is
> LGPLv3+. Both license texts ship with the app's credits and the repo.

## make-me-a-hanzi — primary source

This is the backbone dataset. Repository: `skishore/makemeahanzi`. Two files matter:

### `graphics.txt` — stroke geometry

One JSON object per line. **⚠ verify exact current field names.** The historically stable
shape is:

```json
{
  "character": "你",
  "strokes": [
    "M 320 717 Q ... L ... Z",
    "M 456 695 Q ..."
  ],
  "medians": [
    [[320,717],[312,683],[230,590],[170,537]],
    [[456,695],[465,660], ... ]
  ]
}
```

- `character` — the single simplified character this record describes.
- `strokes` — array of SVG path strings (`M`/`L`/`Q`/`C`/`Z`), **one per stroke**, in
  correct writing order. Coordinate space is a ~**1024×1024 font em-square with the Y axis
  pointing up** (glyphs sit in a 900-unit box with the baseline offset below it; the
  transform hanzi-writer applies is ≈ `translate(0, 900) scale(1, -1)`). **Rendering
  without the Y-flip draws every character upside down** — the ingest tool applies and
  validates this transform during normalization (see pipeline below). These paths define
  the *outline* of each stroke for rendering the completed character and the demo
  animation.
- `medians` — array of polylines, **one per stroke** (parallel to `strokes`), each a list
  of `[x,y]` points tracing the stroke's **centerline / direction**. This is what the
  grading engine compares a user's stroke against (see `05-stroke-engine.md`).

> **Why medians, not stroke outlines?** A user's freehand stroke is a 1D line; comparing
> it to another 1D line (the median) is tractable and forgiving. Comparing it to the 2D
> filled outline of the correct stroke would be far stricter and latency-heavy.

### `dictionary.txt` — per-character dictionary

One JSON object per line. **⚠ verify exact current field names.** Stable shape:

```json
{
  "character": "你",
  "pinyin": ["nǐ"],
  "definition": "you (plural); second person",
  "decomposition": "⺅尔",
  "etymology": { "type": "pictographic", "hint": "..." },
  "radical": "⺅",
  "matches": [[...]]
}
```

- `pinyin` — array of readings (one char may have several). Tone-marked.
- `definition` — short English gloss (often multi-clause, `;`-separated).
- `decomposition` — the character broken into components (heuristic, not always perfect).
- `radical` — main radical, useful for grouping/browsing.
- `etymology`, `matches` — optional; we surface etymology hints if present, ignore
  `matches` (component-matching data) in MVP.

License note: `dictionary.txt` is **LGPLv3+** (unlike `graphics.txt` — see the repo's
`COPYING`); ship the LGPL text alongside the Arphic license in credits.

## CC-CEDICT — words & cross-check

Format: one entry per line, `#`-prefixed comments are metadata.

```
 Traditional Simplified [pin1 yin1] /English gloss 1/English gloss 2/
 你好 你好 [ni3 hao3] /Hello/Hi/
```

- We use it for **multi-char words** (`Word` table in `03`) — picking the most common,
  shortest words containing each character to use as examples.
- Also used to **cross-check / enrich** make-me-a-hanzi single-char definitions where the
  gloss is thin.
- **Phase 0 prototype slice:** a `phrases.json` (2-3 phrases per prototype character) was
  produced from CC-CEDICT by auto-ranking candidate words (containing the character,
  short, proper-nouns/vulgar entries filtered) then a manual final pick for
  kid-appropriateness — authentic CEDICT pinyin/gloss, human-curated selection. This is
  the small-scale rehearsal of the eventual `Word`-table selection rule.
- License: **CC BY-SA 4.0** (updated from the historical 3.0). Attribution ships on the
  credits screen; share-alike governs the license of our *derived* dataset (see summary
  below).

## hanzi-writer-data — prototype source & sanity check

`chanind/hanzi-writer-data` republishes make-me-a-hanzi data as per-character JSON files
(same lineage, same Arphic terms). Two uses:

- **Phase 0 prototype source:** the stroke-engine prototype checks in 20 per-character
  JSON files directly (no ingest pipeline yet) — see `08-roadmap.md`. The prototype also
  checks in a **20-entry dictionary slice** (pinyin + English definitions) extracted from
  make-me-a-hanzi's `dictionary.txt` (LGPL v3+ — attribution ships in the data NOTICE),
  so characters are never shown as bare shapes even before the real pipeline exists.
- **Ingest sanity check:** verify that our ingestion of `graphics.txt` produces identical
  stroke/median data for a sample of characters, and cross-reference field naming if
  make-me-a-hanzi's raw files have drifted.

The production pipeline still ingests make-me-a-hanzi directly, not this derivation.

## Unihan — cross-check metadata

Unicode's Unihan database (`Unihan_IRGSources.txt`, etc.). Key fields:

- `kTotalStrokes` — total stroke count (cross-check against `len(strokes)` from
  make-me-a-hanzi; flag mismatches).
- `kRSUnicode` — radical/stroke count; cross-check radical.

> **`kFrequency` no longer exists.** The original plan ordered the curriculum by Unihan's
> `kFrequency`, but that property was provisional and **has been removed from Unihan**
> (and even when present it was a 5-bucket tier over a subset of characters, not a rank).
> Frequency is now computed at ingest — next section.

License: Unicode license (permissive with attribution); include the current text in the
attribution manifest at ingest.

## Character frequency — derived at ingest

The ingest tool computes its own character frequency ranks (`Character.freqRank`):

- Count each character's occurrences across the **full Tatoeba `cmn` corpus** (all
  Mandarin sentences, not just the selected examples); rank descending. Ties broken by
  CEDICT word-membership count, then by codepoint — fully deterministic.
- A sentence corpus skews conversational. That's acceptable: the rank only orders ~174
  HSK 1 characters *relative to each other* for teaching, it is not a published
  linguistic artifact.
- **Upgrade path:** Jun Da's Modern Chinese frequency list is the academic standard, but
  its redistribution license is unclear (**⚠ verify before adopting**); swapping it in
  later is a one-column change confined to the ingest tool.

## Tatoeba — example sentences

Tatoeba publishes three relevant files:

- `sentences.csv` — `sentence_id, lang, text`. We filter `lang = "cmn"` (Mandarin). The
  full filtered corpus also feeds the frequency ranks above. (Prefer
  `sentences_detailed.csv`, which carries the contributing username for attribution;
  ⚠ verify exact export columns at ingest.)
- `links.csv` — pairs of `sentence_id`s that are translations of each other.
- (optional) `tags.csv`, `user_lists.csv` — not needed for MVP.

**Selection rule per character (amended 2026-07-09; narrowed 2026-07-10 at M2
implementation):** the **curated LLM sentence** (next section) is the primary example;
where none passed review, the character's CEDICT words are the fallback context.
Tatoeba's M2 role is **frequency only** — shipping its sentences would require the
~600 MB English corpus for translations via `links`, which isn't worth it while the
LLM sentences cover the full curriculum. Tatoeba sentences remain the documented
upgrade path if a broader pool is ever wanted (the `Sentence` table already models
them).

**⚠ verify license:** historically CC-BY 2.0 FR; Tatoeba's licensing has shifted per-
sentence. Safest path: record the contributing user + sentence id so attribution is exact,
and prefer sentences under a permissive license where the data exposes it.

## LLM-generated sentences — primary example source (added 2026-07-09)

Datasets can't give us what a language learner actually needs from an example sentence:
**comprehensible input** — sentences built only from characters the learner has already
met, in an all-ages tone. An LLM can. Constitution amendment 2026-07-09 allows it under
strict rules; scheduled as milestone M2 (`08`).

- **Ingest-time only.** The `:data-ingest` tool calls the Claude API once per curriculum
  character; the app never calls an LLM at runtime (offline-first is untouched).
- **Vocabulary-constrained.** The prompt restricts each sentence to characters at or
  below the target character's curriculum position (plus the target itself), so early
  learners can actually read their examples. Length ≤ ~10 characters; tone all-ages
  playful, never childish (`07`).
- **Human-reviewed, then checked in.** Generated candidates land in a review file; a
  human (dad) approves/edits/rejects each; only approved sentences enter the dataset.
  The checked-in, reviewed file — not the API — is the pipeline's input, so
  `./gradlew :data-ingest:run` stays deterministic and offline. Regeneration is a
  deliberate, separate step.
- **Provenance recorded.** Each sentence row carries `source = "llm"` plus the pinned
  model id; prompts and model version live in the repo (e.g. `data-ingest/prompts/`).
  English glosses and tone-marked pinyin are generated alongside and reviewed together.
- **Pinyin/gloss cross-check.** The tool validates every LLM sentence's characters exist
  in the dataset and cross-checks readings against CEDICT; mismatches fail review, not
  silently pass.

## Pre-generated TTS audio — shipped as data (added 2026-07-09)

Device TTS varies wildly and is simply absent on phones without a Mandarin voice pack.
Per `01` (and the constitution's allowed quality upgrade), the pipeline pre-generates
audio at ingest time — moved up from old Phase 3 into milestone M2 (`08`):

- A cloud TTS API (e.g. Google Cloud TTS) is called once per curriculum character,
  phrase, and sentence; clips ship as app/data assets — offline forever, no runtime
  service, never an API key in the app (`12`).
- Like the LLM step, generation is a **deliberate, separate step** whose output is
  checked in / cached; the deterministic ingest run consumes the stored clips.
- The manifest records voice name + API version per batch. `PregenAudioSpeechService`
  plays clips with device-TTS fallback for anything uncovered (`01`).

## License obligations summary

> This section is a checklist, not legal advice. The app is a **free/open product**
> (constitution), so share-alike terms are workable — we comply rather than avoid.
> Complete every box before the first public release (Publish milestone).

- [ ] **Arphic Public License** (`graphics.txt`): copyleft for the font-derived data —
      ship the APL text + attribution in-app (Credits) and in the repo; derived stroke
      data remains under APL.
- [ ] **LGPL v3+** (`dictionary.txt`): ship the license text + attribution; keep the
      derived dictionary data replaceable/extractable (the bundled SQLite is regenerable
      from public sources via the ingest tool, which satisfies the spirit and letter).
- [ ] **CC BY-SA 4.0** (CC-CEDICT): attribution + share-alike — CEDICT-derived rows in
      our dataset are redistributed under CC BY-SA 4.0.
- [ ] **Unicode license** (Unihan): permissive; include current text in the manifest.
- [ ] **Tatoeba**: mostly CC-BY 2.0 FR but licensed **per sentence** — record sentence id
      + contributor at ingest; credits screen lists contributors; skip sentences whose
      license the export marks as non-permissive.
- [ ] **LLM-generated sentences**: our own generated, human-reviewed content — no
      third-party license obligation; released under the same terms as the rest of the
      derived dataset; provenance (model id, generation date, reviewer) ships in the
      attribution manifest and the credits screen notes that example sentences are
      AI-generated and human-reviewed.
- [ ] **Our releases:** app code under MIT or Apache-2.0; the *derived bundled dataset*
      under the terms of its most restrictive inputs per component (APL for stroke data,
      LGPL for dictionary-derived, CC BY-SA 4.0 for CEDICT/Tatoeba-derived), documented in
      the attribution manifest the ingest tool emits.

## Ingestion pipeline

A **JVM Kotlin tool** (Gradle subproject `:data-ingest`, **not shipped** in the APK) that:

1. Reads the raw source files from a local `data/raw/` directory (checked in or downloaded
   by a Gradle task; never fetched at app runtime). The HSK 1 word/character list is one
   of these pinned inputs.
2. Parses each into intermediate Kotlin data classes; **validates schema** (field names,
   parallel `strokes`/`medians` arrays) and fails loudly with a diff on drift.
3. **Normalizes geometry:** applies the Y-flip from make-me-a-hanzi's font space and
   scales into the internal **1000×1000, Y-down** box (see `05`); validates by comparing
   a rendered sample against hanzi-writer-data.
4. **Joins on the simplified character** as the natural key:
   make-me-a-hanzi `graphics` + `dictionary` ← CEDICT words ← reviewed LLM sentences
   ← Tatoeba sentences.
5. Computes **frequency ranks** from the Tatoeba corpus (see above) and applies selection
   rules (curated LLM sentence first, then shortest Tatoeba sentence, then CEDICT word;
   words ranked by frequency).
6. Bundles the **pre-generated TTS clips** (see above) for every curriculum character,
   phrase, and sentence, validating coverage (a curriculum entry with no clip is a
   loud warning; device TTS covers the gap at runtime).
7. Emits **`CurriculumEntry` rows** (`curriculumId="hsk"`, level, sequence — see `03`/`04`)
   and tags all content rows `lang = "zh-Hans"` (BCP-47).
8. **Fails the build** if any character in an MVP curriculum level lacks complete data
   (strokes + medians + definition). Silent drops are allowed only for non-curriculum
   characters, which are logged.
9. Writes a **versioned SQLite file** (`hanzi_vN.sqlite`) into `app/src/main/assets/databases/`,
   matching the Room schema in `03-data-model.md`.
10. Emits an **ingest report**: counts per source, characters dropped, the
    license-attribution manifest (including full APL + LGPL texts, per-sentence Tatoeba
    contributors, and LLM/TTS generation provenance — model, voice, date, reviewer),
    and the dataset version string.

### Dataset versioning

- A single `datasetVersion` string (e.g. `mmah-2024-01+cedict-2024-03+unihan-15.1`).
- Stored in a `Meta` table in the DB and checked by the app on launch. If the bundled
  version differs from the on-disk version, the app copies the new asset and triggers a
  Room migration / re-seed of user-facing derived rows (user progress is preserved).

### Reproducibility

- Ingestion is deterministic: same inputs → same bytes. No clock-based ordering, no
  network calls. Pinned source snapshots (by date) recorded in the ingest report.
- A `./gradlew :data-ingest:run` reproduces the asset from `data/raw/`.
- The **generation steps** (LLM sentences, TTS clips) are the only network-touching
  parts and run as separate, deliberate tasks whose reviewed/stored outputs are checked
  in — the deterministic ingest run consumes those files and never calls an API.

## Data size & shape (estimates, ⚠ verify with real files)

- ~10,000 characters in make-me-a-hanzi graphics; MVP uses ~174 (HSK 1).
- `graphics.txt` for the full set is a few MB; the MVP slice (HSK 1 + examples) compresses
  to well under 1 MB. APK impact is negligible.
- Tatoeba sentences for Mandarin: tens of thousands; we filter to one per character → tiny.

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Field names in make-me-a-hanzi drift from this spec | Ingest tool validates schema and fails loudly with a diff; ⚠ verify at M2 (data pipeline). |
| Y-flip / coordinate assumptions wrong in detail | Tool auto-detects bounds from a sample, applies the documented transform, and diff-validates rendered geometry against hanzi-writer-data. |
| A license forbids bundling (e.g. Arphic terms) | Keep ingestion swappable: if a source can't be bundled, fall back to on-first-launch download with attribution, still offline thereafter. Constitution allows one-time import. |
| An MVP-curriculum character lacks strokes/medians/definition | **Ingest fails the build** — the curriculum promise is "all of HSK 1" (constitution). Non-curriculum gaps are dropped + logged. |
| Tatoeba-derived frequency skews conversational | Acceptable for intra-level ordering; Jun Da's list is the documented upgrade (license ⚠). |
| Tatoeba attribution complex | Record per-sentence contributor + id; credits screen lists contributors. |
