# Pilot stroke corpus

The M4.1 release gate needs evidence that the grader is fair in real hands, not just on
synthetic paths:

- **>= 200 honest finger-written strokes** (drawn on the highlighted stroke, no deliberate
  error) with **false rejection < 5%**.
- **>= 100 deliberate direction/order errors** with **false acceptance < 1%**.
- Across the starter characters (人 大 天) and **at least two phone sizes**.

The debug APK already writes the raw attempts locally; this directory turns them into the
report. Raw exports stay on the analyst's machine and are git-ignored.

## 1. Record on the phone (debug build)

In **Settings → Local pilot stroke export**:

1. Turn export on (this is the consent switch; nothing leaves the phone).
2. Pick the **Facilitator label** before each phase:
   - `Honest` — the learner draws the stroke as well as they can.
   - `Deliberate error` — the facilitator asks for a wrong direction or a later stroke.
3. Run the honest phase on phone A, then on phone B. Repeat with the deliberate label on
   both phones.

The label is written into every JSONL record, so the analysis never has to infer ground
truth from the verdict. Leave the label on `Unlabeled` outside a recorded phase — those
records are reported but excluded from the rates.

Two phone sizes are required. The exporter records `screenWidthDp` / `screenHeightDp`
rather than pixels, so the report is stable across densities.

## 2. Pull the exports

```bash
mkdir -p data/corpus/raw
adb devices
adb shell run-as io.github.brad1014z.hanzi ls files/pilot-strokes
```

Pull each session (repeat for every file, including the second phone):

```bash
adb exec-out run-as io.github.brad1014z.hanzi \
  cat files/pilot-strokes/<session>.jsonl > data/corpus/raw/<session>.jsonl
```

If `run-as` is unavailable, use **Settings → Local pilot stroke export** only on a
debuggable build; the files live in the app's private `files/pilot-strokes/` directory.
There is no upload path by design.

## 3. Analyze

```bash
# Writes data/corpus/corpus-report.md and prints the rates.
./gradlew :data-ingest:analyzePilotCorpus

# Same, but exits non-zero unless every release-gate condition is met.
./gradlew :data-ingest:checkPilotCorpus
```

The report contains:

- honest scored attempts, false rejections, FRR;
- deliberate scored attempts, false acceptances, FAR;
- per-character and per-phone-size breakdowns;
- the blocking gaps, if any.

`ignored` attempts (accidental contact below the minimum stroke length) are excluded from
both denominators. `unlabeled` records are excluded from the rates and counted separately.
A record with an unknown label fails the gate rather than being silently guessed.

## What to commit

- **Do commit** `data/corpus/corpus-report.md` once the corpus is real — it is the gate
  evidence, and it contains no session IDs or raw coordinates.
- **Do not commit** `data/corpus/raw/`. It is pseudonymous (random session ID, device
  model, geometric points), but it is still pilot data and has no place in the repo.
  Delete it after the report is reviewed, per the consent script.
