# 03 — Data Model

> **Status:** DRAFT
> Room (SQLite) schema. Two classes of data: **content** (read-only, seeded by the ingest
> tool — see `02`) and **user** (mutated at runtime). The split matters because a dataset
> update replaces content tables but must preserve user tables.

## Design rules

- The **simplified character** is the natural primary key for content (`character = "你"`).
  Stable, human-readable, and the join key across all sources.
- Content tables are **read-only at runtime**. The app never writes to them; only the
> ingestion tool and dataset migrations do.
- User tables reference content by the character key (FK) so a content refresh doesn't
  orphan progress.
- Coordinates (stroke paths, medians) are stored in a normalized **1000×1000** box (see
  `05-stroke-engine.md`); the UI scales at draw time.
- JSON columns hold small, structured blobs (pinyin variants, point arrays). Room stores
  them as `TEXT`; parsing is a typed responsibility of the repository, not the UI.

## Entities

### Content tables

#### `Character` — one row per character we teach
```
character     TEXT PRIMARY KEY     -- e.g. "你"
pinyin        TEXT NOT NULL        -- JSON array: ["nǐ"]
definition    TEXT NOT NULL        -- primary English gloss
radical       TEXT                 -- e.g. "⺅" (nullable: rare gaps)
strokeCount   INTEGER NOT NULL     -- len(strokes), cross-checked vs Unihan
freqRank      INTEGER              -- from Unihan kFrequency-derived rank (nullable)
hskLevel      INTEGER              -- 1..6; nullable for non-HSK chars
decomposition TEXT                 -- component hint, e.g. "⺅尔" (nullable)
etymologyHint TEXT                 -- short text if present (nullable)
```

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
simplified    TEXT NOT NULL        -- e.g. "你好"
traditional   TEXT                 -- nullable
pinyin        TEXT NOT NULL        -- space-separated, tone-marked
english       TEXT NOT NULL        -- gloss
freqRank      INTEGER              -- for ranking examples (nullable)
```
Plus join table `WordCharacter(wordId, character, position)` so we can query
"words containing character X, ranked."

#### `Sentence` — example sentences (from Tatoeba)
```
id            INTEGER PRIMARY KEY                  -- Tatoeba sentence id
text          TEXT NOT NULL                        -- Mandarin sentence
english       TEXT                                 -- linked English translation (nullable)
source        TEXT NOT NULL                        -- "tatoeba"
contributor   TEXT                                 -- for attribution (nullable)
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

- `Character(hskLevel)` — curriculum queries.
- `Character(freqRank)` — frequency ordering.
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
- No analytics events.
- (Phase 2 adds account/sync metadata below — but still no per-stroke drawings, and still
  no analytics.)

---

## Phase 2 tables (Arcade & competition)

> **Status:** DRAFT · Phase 2. These tables are added in Phase 2a/2b/2c. For the cloud-sourced
> data (profiles, league standings, leaderboards), the **server is source of truth**; these
> local rows are **mirrors/caches + the sync outbox**. Game sessions and local high scores
> are local-only (server never sees the full session, only the validated submission).

### Local-only (game state)

#### `GameSession` — one row per arcade play
```
id            INTEGER PRIMARY KEY AUTOINCREMENT
mode          TEXT NOT NULL        -- SPRINT | DAILY | TOWER | SPEED
characterSetId TEXT                -- identifies the char set played (date-seed for DAILY)
score         INTEGER NOT NULL
xp            INTEGER NOT NULL
startedAt     INTEGER NOT NULL     -- epoch millis
durationMs    INTEGER NOT NULL
events        TEXT                 -- JSON compact play log (per-char verdicts + ts)
synced        INTEGER NOT NULL DEFAULT 0   -- 0/1: has the outbox drained this session?
```
Index on `(mode, startedAt)` for per-mode history; `synced` drives outbox drain queries.

#### `LocalHighScore` — best score per mode (one row per mode)
```
mode          TEXT PRIMARY KEY     -- SPRINT | DAILY | TOWER | SPEED
bestScore     INTEGER NOT NULL
bestXp        INTEGER NOT NULL
achievedAt    INTEGER NOT NULL
```

### Sync outbox (drained by WorkManager — see `10`)

#### `PendingXpSync` — queued submissions
```
id            INTEGER PRIMARY KEY AUTOINCREMENT
sessionId     INTEGER NOT NULL     -- FK -> GameSession
submissionUuid TEXT NOT NULL       -- client-generated; server dedupes on this
attempts      INTEGER NOT NULL DEFAULT 0
queuedAt      INTEGER NOT NULL
lastError     TEXT                 -- nullable; last failure reason
```
Drained on connectivity change / sign-in; row deleted on confirmed server success.
Idempotency via `submissionUuid` (retries can't double-count XP).

### Cloud mirrors (read-mostly caches; server is source of truth)

#### `Profile` — cached local profile (if signed in)
```
uid           TEXT PRIMARY KEY     -- Firebase/Supabase uid
handle        TEXT NOT NULL        -- auto-generated anonymous handle
leagueTier    INTEGER              -- current league tier (nullable pre-first-placement)
signedInAt    INTEGER NOT NULL
```

#### `LeagueMembership` — cached current-week league
```
uid           TEXT NOT NULL
weekStart     INTEGER NOT NULL     -- Monday 00:00 UTC, epoch millis
leagueId      TEXT NOT NULL
tier          INTEGER NOT NULL
totalXp       INTEGER NOT NULL DEFAULT 0
rank          INTEGER              -- nullable until computed
cachedAt      INTEGER NOT NULL
PRIMARY KEY (uid, weekStart)
```

#### `DailyLeaderboardEntry` — cached daily challenge standings (optional cache)
```
date          INTEGER NOT NULL     -- UTC date key
uid           TEXT NOT NULL
handle        TEXT NOT NULL
bestScore     INTEGER NOT NULL
bestXp        INTEGER NOT NULL
cachedAt      INTEGER NOT NULL
PRIMARY KEY (date, uid)
```

### Phase 2 migration notes

- All Phase 2 tables are **additive** — added via a single `Migration(N, N+1)` that creates
  them. No existing Phase 1 table is altered, so the user-data-preservation rule holds
  trivially.
- If a user **never signs in**, only `GameSession` + `LocalHighScore` are ever written; the
  cloud mirrors + outbox stay empty. The app works identically to the no-account experience.
- On **sign-out / account deletion**, `GameSession`/`LocalHighScore` are **kept** (they're
  the user's local history); cloud mirrors + outbox are cleared.

## Open questions

- [ ] Should `Word` / `Sentence` use the natural text as PK instead of a surrogate id?
      Lean: surrogate id keeps CEDICT/Tatoeba dedup simple.
- [ ] Store pinyin with or without tone numbers vs tone marks? Decision: store tone-**marked**
      for display; engine can derive numbers if needed. Confirm against source formats.
- [ ] (Phase 2) Prune old `GameSession` rows after N days? Lean: keep a rolling window
      (e.g. 90 days) + aggregate; drop detail.
