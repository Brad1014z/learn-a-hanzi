# 02 — Data Sources

> **Status:** DRAFT
> This is one of the two riskiest specs. Every fact about an upstream format or license is
> marked **⚠ verify** where it must be confirmed against the live source before the
> corresponding ingestion code ships. The shape of the data is stable, but field names and
> license clauses change over time — confirm, don't assume.

## Source inventory

| Source | Provides | License (⚠ verify) | Used for |
|--------|----------|--------------------|----------|
| [make-me-a-hanzi](https://github.com/chanind/make-me-a-hanzi) | stroke SVG paths, medians, dictionary | **Arphic Public License** (graphics) | stroke rendering + grading, definitions, decomposition |
| [CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cedict) | word list (trad/simpl/pinyin/english) | **CC-BY-SA 3.0** | definitions cross-check, multi-char words |
| [hanzi-writer-data](https://github.com/chanind/hanzi-writer-data) | derived JSON per character | Arphic / same lineage | **reference / sanity check only** — we ingest make-me-a-hanzi directly |
| [Unihan database](https://www.unicode.org/charts/unihan.html) | readings, frequency, radical info | Unicode Data Files agreement | frequency ranks, curriculum ordering |
| [Tatoeba](https://tatoeba.org/en/downloads) | sentences + translation links | **CC-BY 2.0 FR** (⚠ verify current) | example sentences/phrases per character |

## make-me-a-hanzi — primary source

This is the backbone dataset. Repository: `chanind/make-me-a-hanzi`. Two files matter:

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
  correct writing order. Coordinate space is a **1024×1024** box (⚠ verify — historically
  true; some derivations normalize differently). These define the *outline* of each stroke
  for rendering the completed character and for the demo animation.
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
- **⚠ verify attribution text** required by CC-BY-SA 3.0 (we'll ship a credits screen).

## hanzi-writer-data — reference only

`chanind/hanzi-writer-data` republishes make-me-a-hanzi data as per-character JSON
(`all.json`, plus `traditional` / `simplified` subsets). We do **not** ingest from it; we
use it to:

- Sanity-check that our ingestion of `graphics.txt` produced identical stroke/median data
  for a sample of characters.
- Cross-reference the JSON field naming if make-me-a-hanzi's raw files have drifted.

## Unihan — frequency & metadata

Unicode's Unihan database (`Unihan_IRGSources.txt`, `Unihan_OtherMappings.txt`, etc.).
Key fields:

- `kFrequency` — a frequency tier (`1` = most frequent … `5` = rare). Drives curriculum
  ordering within HSK levels and for the future frequency-only track.
- `kTotalStrokes` — total stroke count (cross-check against `len(strokes)` from
  make-me-a-hanzi; flag mismatches).
- `kRSUnicode` — radical/stroke count; cross-check radical.

License: Unicode Data Files agreement (permissive with attribution). **⚠ verify current
text.**

## Tatoeba — example sentences

Tatoeba publishes three relevant files:

- `sentences.csv` — `sentence_id, lang, text`. We filter `lang = "cmn"` (Mandarin).
- `links.csv` — pairs of `sentence_id`s that are translations of each other.
- (optional) `tags.csv`, `user_lists.csv` — not needed for MVP.

**Selection rule per character:** for each character, pick the **shortest** Mandarin
sentence (≤ ~12 chars) that (a) contains the character and (b) has an English translation
via `links`. If none, fall back to a CEDICT multi-char word as the "phrase." This keeps
every character displayable even when no good sentence exists.

**⚠ verify license:** historically CC-BY 2.0 FR; Tatoeba's licensing has shifted per-
sentence. Safest path: record the contributing user + sentence id so attribution is exact,
and prefer sentences under a permissive license where the data exposes it.

## License obligations summary

> This section is a checklist, not legal advice. Confirm each before public release.

- [ ] **Arphic Public License** (make-me-a-hanzi graphics): read the full text; it has
      specific attribution and (historically) distribution conditions. Display attribution
      in-app (Credits screen) and in the repo. ⚠ verify
- [ ] **CC-BY-SA 3.0** (CC-CEDICT): attribution + share-alike. Affects how we license any
      *derived* dataset we redistribute. ⚠ verify
- [ ] **Unicode Data Files** (Unihan): attribution, permissive. ⚠ verify
- [ ] **Tatoeba** (sentences): CC-BY 2.0 FR or per-sentence; record per-sentence
      attribution. ⚠ verify
- [ ] Decision needed: under what license do **we** release the app and the *derived*
      bundled dataset? Recommendation: app code under a permissive license (MIT/Apache-2.0);
      derived data under whatever the most restrictive source (CC-BY-SA) requires.

## Ingestion pipeline

A **JVM Kotlin tool** (Gradle subproject `:data-ingest`, **not shipped** in the APK) that:

1. Reads the raw source files from a local `data/raw/` directory (checked in or downloaded
   by a Gradle task; never fetched at app runtime).
2. Parses and normalizes each into intermediate Kotlin data classes.
3. **Joins on the simplified character** as the natural key:
   make-me-a-hanzi `graphics` + `dictionary` ← CEDICT words ← Tatoeba sentences ← Unihan
   frequency.
4. Applies selection rules (one example phrase/sentence per char, shortest wins; words
   ranked by frequency).
5. Writes a **versioned SQLite file** (`hanzi_vN.sqlite`) into `app/src/main/assets/databases/`,
   matching the Room schema in `03-data-model.md`.
6. Emits an **ingest report**: counts per source, characters dropped (no graphics, no
   definition), license-attribution manifest, and the dataset version string.

### Dataset versioning

- A single `datasetVersion` string (e.g. `mmah-2024-01+cedict-2024-03+unihan-15.1`).
- Stored in a `Meta` table in the DB and checked by the app on launch. If the bundled
  version differs from the on-disk version, the app copies the new asset and triggers a
  Room migration / re-seed of user-facing derived rows (user progress is preserved).

### Reproducibility

- Ingestion is deterministic: same inputs → same bytes. No clock-based ordering, no
  network calls. Pinned source snapshots (by date) recorded in the ingest report.
- A `./gradlew :data-ingest:run` reproduces the asset from `data/raw/`.

## Data size & shape (estimates, ⚠ verify with real files)

- ~10,000 characters in make-me-a-hanzi graphics; MVP uses ~178 (HSK 1).
- `graphics.txt` for the full set is a few MB; the MVP slice (HSK 1 + examples) compresses
  to well under 1 MB. APK impact is negligible.
- Tatoeba sentences for Mandarin: tens of thousands; we filter to one per character → tiny.

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Field names in make-me-a-hanzi drift from this spec | Ingest tool validates schema and fails loudly with a diff; ⚠ verify at Phase 0. |
| Coordinate space isn't actually 1024×1024 | Tool auto-detects bounds from a sample and normalizes to our internal 1000×1000 space (see `05`). |
| A license forbids bundling (e.g. Arphic terms) | Keep ingestion swappable: if a source can't be bundled, fall back to on-first-launch download with attribution, still offline thereafter. Constitution allows one-time import. |
| A character has no median data | Exclude from MVP curriculum; log in ingest report. |
| Tatoeba attribution complex | Record per-sentence contributor + id; credits screen lists contributors. |
