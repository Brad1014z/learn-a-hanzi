# Teacher content review — M4.1 first 30

This directory is the **review workspace** for the signed lesson manifest, not shipped
content. Nothing here is approved, and nothing here is read by the app.

The release gate is documented in
[`docs/milestones/m4.1-release-gates.md`](../../docs/milestones/m4.1-release-gates.md):
a Chinese teacher must review and sign exactly 30 lessons before a signed APK can be built.

## Generate the draft

```bash
./gradlew :data-ingest:draftLessonContent
```

It reads the bundled dataset (`app/src/main/assets/databases/hanzi_v1.sqlite`) and audio,
then writes:

| File | Purpose |
|---|---|
| `lesson-content-first30/lesson-content-first30.draft.json` | the editable manifest (all `review.approved = false`) |
| `lesson-content-first30/lesson-content-first30-review.html` | self-contained review sheet with embedded audio |
| `lesson-content-first30/lesson-content-first30-review.csv` | the same content for spreadsheet comments |

The generator picks 人 → 大 → 天 followed by the app's curriculum order, fills readings,
tones, proposed learner glosses, audio paths, and the app's world/sequence, and includes a
dataset example **only** when it already uses only characters learned by that lesson.
Otherwise the example is omitted and the review sheet says so. Proposed glosses are the
first dictionary sense, not a pedagogical choice — the sheet flags every one for review.

To review a different set (it must still start 人 → 大 → 天), pass the order explicitly:

```bash
./gradlew :data-ingest:draftLessonContent --args="--characters 人,大,天,一,二,三,..."
```


It validates every structural rule with approvals switched off. It never writes
`approved = true`, never edits `app/src/main/assets/content/`, and never infers approval.

## Review

1. Open the HTML (works offline; audio is embedded) or the CSV.
2. Correct readings, tones, learner glosses, examples, and translations in the JSON.
3. For a lesson with no compliant example, either write one using only the characters
   listed under “allowed so far”, or leave the example empty. The validator accepts an
   empty example; it never accepts one that introduces future characters.
4. Write pinyin **syllable-segmented** (`jīn tiān`, not `jīntiān`). The target-reading
   check splits on spaces, so word-segmented pinyin can hide the target.
5. When the whole set is correct, set `review.approved` to `true`, fill `review.reviewer`
   and `review.reviewedAt` for **every** lesson, then copy the file to
   `app/src/main/assets/content/lesson-content-first30.json`.

## Validate

```bash
# CI / local: an empty manifest is pending, a non-empty one must be fully valid
./gradlew :data-ingest:verifyLessonManifest

# the signing gate: an empty or invalid manifest fails
./gradlew :data-ingest:verifyPilotLessonManifest
```

Both checks validate against the bundled dataset and audio, so they work without
`data/raw/`. The signed-release build also runs the strict check before packaging.
