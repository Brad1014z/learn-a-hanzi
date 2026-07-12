# 03 — Data Model

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-09 — `Sentence.source`
> gains `"llm"`; user tables land first, in milestone M1, ahead of content tables;
> amended 2026-07-10 (M2 implementation) — `CurriculumEntry` gains `world`/`worldName`,
> `Sentence` gains `pinyin`/`forCharacter`, FKs are conceptual not declared)
> Room (SQLite) schema. Two classes of data: **content** (read-only, seeded by the ingest
> tool — see `02`) and **user** (mutated at runtime). The split matters because a dataset
> update replaces content tables but must preserve user tables.
>
> **M1 note (2026-07-09):** the **user tables** below ship first, inside the Phase 0
> prototype app (roadmap milestone M1), shaped exactly as specified here — so the SRS
> milestone (M3) is an engine change, not a schema migration. Content tables arrive with
> the ingest pipeline (M2).

## Design rules

- The **simplified character** is the natural primary key for content (`character = "你"`).
  Stable, human-readable, and the join key across all sources.
- All content rows carry a **`lang` tag** (BCP-47, script-qualified: `zh-Hans` for the
  MVP) so future languages/scripts (`zh-Hant`, `ja`, `ko`) are *data additions*, not
  schema migrations. Single-column PKs stay for the MVP; cross-language codepoint
  collisions (e.g. 学 in zh and ja) are resolved in `09-extension-paths.md` when a second
  language actually lands.
- Content tables are **read-only at runtime**. The app never writes to them; only the
  ingestion tool and dataset migrations do.
- User tables reference content by the character key so a content refresh doesn't
  orphan progress. *(M2 note: the FK is conceptual — Room entities don't declare it,
  because progress may legitimately reference non-curriculum characters, e.g. the
  Phase 0 prototype set, and a dataset swap must never cascade into user tables.
  Referential integrity is the ingest tool's job.)*
- Coordinates (stroke paths, medians) are stored in a normalized **1000×1000** box (see
  `05-stroke-engine.md`); the UI scales at draw time.
- JSON columns hold small, structured blobs (pinyin variants, point arrays). Room stores
  them as `TEXT`; parsing is a typed responsibility of the repository, not the UI.

## Entities

### Content tables

#### `Character` — one row per character we teach
```
character     TEXT PRIMARY KEY     -- e.g. "你"
lang          TEXT NOT NULL        -- BCP-47, "zh-Hans" in MVP
pinyin        TEXT NOT NULL        -- JSON array: ["nǐ"]
definition    TEXT NOT NULL        -- primary English gloss
radical       TEXT                 -- e.g. "⺅" (nullable: rare gaps)
strokeCount   INTEGER NOT NULL     -- len(strokes), cross-checked vs Unihan
freqRank      INTEGER              -- Tatoeba-corpus-derived rank, see 02 (nullable)
decomposition TEXT                 -- component hint, e.g. "⺅尔" (nullable)
etymologyHint TEXT                 -- short text if present (nullable)
```
> Curriculum membership (formerly an `hskLevel` column) lives in `CurriculumEntry` below —
> one mechanism for HSK levels now and the frequency track / JLPT / other curricula later.

#### `CurriculumEntry` — membership of a character in a curriculum
```
curriculumId  TEXT NOT NULL        -- "hsk" in MVP; later e.g. "freq", "jlpt"
character     TEXT NOT NULL        -- FK -> Character
level         INTEGER NOT NULL     -- e.g. HSK level 1..6
sequence      INTEGER NOT NULL     -- world-major teaching order, computed by ingest
world         TEXT NOT NULL        -- curated world id (spec 04/10) — added M2
worldName     TEXT NOT NULL        -- placeholder display name (naming: spec 11) — added M2
PRIMARY KEY (curriculumId, character)
```
> `sequence` is the deterministic teaching order — **world-major** (worlds ordered by
> aggregate corpus frequency, then `04`'s frequency/stroke-count/radical order within a
> world), computed once by the ingest tool so the app never re-derives ordering.
> User-built custom decks (M5+) would mirror this shape in a *user* table rather than
> extending this content table.

#### `StrokePath` — one row per stroke of a character
```
character     TEXT NOT NULL        -- FK -> Character
strokeIndex   INTEGER NOT NULL     -- 0-based, writing order
pathData      TEXT NOT NULL        -- SVG path string (outline) for rendering
median        TEXT NOT NULL        -- JSON array of [x,y] points (centerline) for grading
PRIMARY KEY (character, strokeIndex)
FOREIGN KEY (character) REFERENCES Character(character)
```
> `pathData` is the SVG outline (filled stroke look). `median` is the centerline polyline
> the grader compares against. Keeping both lets the renderer draw the pretty outline while
> the engine grades on the cheap centerline.

#### `Word` — multi-char words (from CC-CEDICT), used as example phrases
```
id            INTEGER PRIMARY KEY AUTOINCREMENT
lang          TEXT NOT NULL        -- BCP-47, "zh-Hans" in MVP
simplified    TEXT NOT NULL        -- e.g. "你好"
traditional   TEXT                 -- nullable
pinyin        TEXT NOT NULL        -- space-separated, tone-marked
english       TEXT NOT NULL        -- gloss
freqRank      INTEGER              -- for ranking examples (nullable)
```
Plus join table `WordCharacter(wordId, character, position)` so we can query
"words containing character X, ranked."

#### `Sentence` — example sentences (LLM-generated primary — see `02`)
```
id            INTEGER PRIMARY KEY                  -- synthetic for llm rows (Tatoeba id if ever shipped)
lang          TEXT NOT NULL                        -- BCP-47, "zh-Hans" in MVP
text          TEXT NOT NULL                        -- Mandarin sentence
pinyin        TEXT                                 -- tone-marked, word-spaced (llm rows) — added M2
english       TEXT                                 -- English translation (nullable)
source        TEXT NOT NULL                        -- "llm" | "tatoeba"
contributor   TEXT                                 -- attribution: pinned model id for llm rows,
                                                   -- Tatoeba username otherwise (nullable)
forCharacter  TEXT                                 -- the character the sentence was crafted for — added M2
```
Plus join table `SentenceCharacter(sentenceId, character, position)`.

### User tables

#### `CharacterProgress` — SRS state per character (one row per character the user has touched)
```
character     TEXT PRIMARY KEY      -- FK -> Character
state         TEXT NOT NULL         -- enum: NEW | LEARNING | REVIEW | RELEARNING
dueAt         INTEGER NOT NULL      -- epoch millis; when the card is next due
intervalDays  REAL NOT NULL         -- current SRS interval
ease          REAL NOT NULL         -- ease factor (SM-2 style, ≥ 1.3)
reps          INTEGER NOT NULL      -- successful reps in a row
lapses        INTEGER NOT NULL      -- times forgotten
lastReviewedAt INTEGER              -- nullable
lastGrade     INTEGER               -- last grade 0..5 (nullable)
```

#### `ReviewLog` — append-only history of every review
```
id            INTEGER PRIMARY KEY AUTOINCREMENT
character     TEXT NOT NULL
reviewedAt    INTEGER NOT NULL      -- epoch millis
grade         INTEGER NOT NULL      -- 0..5 (SM-2 grade)
drawnCorrectly INTEGER NOT NULL    -- 0/1: did the grader accept the final stroke set?
durationMs    INTEGER               -- how long the practice card took (nullable)
session       TEXT                  -- review session id grouping a sitting (nullable)
```
Index on `(character, reviewedAt)` for per-character history; index on `reviewedAt` for
"today's reviews" queries.

#### `Meta` — single-row app/dataset metadata
```
key           TEXT PRIMARY KEY      -- e.g. "datasetVersion", "schemaVersion", "createdAt"
value         TEXT NOT NULL
```

## Indexes

- `CurriculumEntry(curriculumId, level, sequence)` — curriculum queries ("next new
  character in HSK 1").
- `Character(freqRank)` — frequency ordering (browse).
- `CharacterProgress(dueAt)` — the daily review queue (the hottest query in the app).
- `WordCharacter(character)` and `SentenceCharacter(character)` — example lookups.

## Type mappings (Room)

| Kotlin type | SQLite column | Notes |
|-------------|---------------|-------|
| `String`    | `TEXT`        | characters, glosses, JSON blobs |
| `Int`/`Long`| `INTEGER`     | counts, ranks, epoch millis |
| `Double`    | `REAL`        | SRS interval, ease |
| enum (`SrsState`) | `TEXT`   | stored as name; converter handles it |
| `List<Point>` / `List<List<Point>>` | `TEXT` | JSON via kotlinx.serialization in repo layer |

## Migrations

- Schema versioned with Room (`version = N`). Every breaking change ships a `Migration(N, N+1)`.
- **Content updates ≠ schema migrations.** A new dataset version is handled by an
  app-level **reseed**: copy the new asset DB, then copy user tables (`CharacterProgress`,
  `ReviewLog`, `Meta` user keys) over. Covered by a `MigrationTestHelper` test.
- User data preservation is the one inviolable rule of any migration.

## What is NOT stored

- No per-stroke user drawings kept long-term. A drawing lives only in memory for the
  current practice card; only the boolean `drawnCorrectly` + grade reach `ReviewLog`.
  (Rationale: privacy, storage; constitution: minimal footprint.)
- No accounts, no sync metadata in MVP.
- No analytics events.

## Open questions

- [x] ~~Natural text vs surrogate id for `Word`/`Sentence`~~ — **decided: surrogate id**
      (keeps CEDICT/Tatoeba dedup simple; Tatoeba's own id is the Sentence PK).
- [x] ~~Tone numbers vs tone marks~~ — **decided: store tone-marked** for display
      (make-me-a-hanzi is already tone-marked; the ingest tool converts CEDICT's tone
      numbers to marks). The engine derives numbers if it ever needs them.
