# 05 — Stroke Engine

> **Status:** ACCEPTED (reviewed 2026-07-05)
> The technically hardest part of the app. This spec describes how the app renders strokes
> and **grades the user's freehand input** against a known target character — entirely in
> native Kotlin on a Compose `Canvas`, no WebView, no third-party library.
>
> **Where the code lives:** all grading math (polyline ops, scoring, SVG path parsing) is
> pure Kotlin in `:core:domain` — no `android.*` imports, JVM-unit-testable, KMP-ready.
> `:feature:practice` contributes Canvas rendering and touch capture only (see `06`).
> The engine is built and tuned first, in the **Phase 0 prototype** (see `08`).
>
> **Prior art, not copied code:** the per-stroke, order + shape grading approach is
> inspired by `hanzi-writer`'s quiz mode (chanind/hanzi-writer, MIT). We re-implement the
> *idea* in Kotlin. ⚠ verify hanzi-writer's license if we ever borrow more than the
> approach.

## Two modes, one renderer

The same `StrokePath` data (outline SVG `pathData` + centerline `median`; see `03`) drives
both:

- **Demo mode** — the app animates the correct stroke order. Used in the "intro" step of
  learning a character and as the "show me" hint during practice.
- **Quiz mode** — the user draws strokes; the engine grades each stroke as it's completed.

## Coordinate space

- All stored geometry is normalized to a **1000×1000, Y-down (screen convention)** box.
  The ingest tool applies the **Y-flip** from make-me-a-hanzi's font space (~1024 em
  square, Y-up, baseline offset — see `02`) so stored data renders upright with no
  per-frame transform. The Phase 0 prototype applies the same flip when loading
  hanzi-writer-data JSON.
- The drawing `Canvas` maps the 1000×1000 box to its pixel size with a single scale +
  translate. Touch input is mapped *back* into 1000-space before grading, so thresholds
  are defined in normalized units and are device-independent.
- A **guide box** (米字格 / rice grid) is drawn behind the character as a writing aid.

## Rendering (shared)

- **Completed strokes / outline:** parse `pathData` (SVG `M/L/Q/C/Z`) with the vendored
  pure-Kotlin parser (see `01` — androidx `PathParser` is a restricted API), adapt to a
  Compose `Path` in the feature module, and fill it. Drawn in the on-screen "ink" color.
- **Demo animation:** for stroke `i`, reveal it by drawing the outline **up to a progress
  fraction** along its `median` — i.e., animate a "growing" stroke by clipping/filling the
  outline region near the median prefix. A simpler MVP approach: draw the median as a
  thick rounded stroke up to the progress fraction (less pretty, far simpler); upgrade to
  outline-clipping in Phase 3 (polish) if needed.
- **Faint target:** in quiz mode, optionally show all strokes at low opacity as a guide
  the user traces. Default is **mode-dependent** — on during first-learn (scaffold the
  unfamiliar shape), off during review (recall from memory is the point) — and
  user-overridable in Settings. Scaffolding that fades; see `07`.

## Quiz mode — grading algorithm

Goal: as the user lifts their finger, decide whether the stroke they just drew is the
**correct next stroke** (right shape, right position, right order) and respond with
feedback. We grade **one stroke at a time** and advance the expected-stroke index on
acceptance.

### Step 1 — Capture & clean the input

1. Collect raw touch points `(x,y)` between pointer-down and pointer-up, mapped into
   1000-space. **Single-pointer policy:** the first pointer down owns the stroke;
   additional simultaneous pointers are ignored (stylus users resting fingers, edge
   touches).
2. **Discard accidental contacts:** a contact with total arc length below
   `MIN_STROKE_UNITS` *and* shorter than ~30 ms is dropped silently (no reject flash) —
   it was a palm graze or a tap, not an attempt. Deliberate dot strokes are far longer
   than this floor.
3. **Simplify** with Ramer–Douglas–Peucker (RDP) at a small epsilon (e.g. 2.0 units) to
   drop redundant points.
4. **Resample** to a fixed point density (e.g. one point every ~10 units along arc length)
   so stroke comparisons aren't biased by drawing speed or point count. Call the result
   `userStroke`: an ordered polyline.

### Step 2 — Match against the expected stroke

Let `expected` be the `median` polyline of the **current expected stroke index** (0-based,
advancing only on accept). Both `userStroke` and `expected` are polylines in 1000-space.

**Length guard (pre-check).** `lenRatio = arcLen(userStroke) / arcLen(expected)` must fall
within `[LEN_RATIO_MIN, LEN_RATIO_MAX]`, else reject before scoring. This catches taps on
long strokes and back-and-forth scribbles that hug the median (which would otherwise score
a deceptively good mean distance).

**Short strokes (dots, 点).** When `arcLen(expected) < SHORT_STROKE_LEN`, tangent sampling
is noise on a 2–3 point polyline: skip the direction score and grade on the position score
plus a single **endpoint vector** check (user's start→end direction vs expected's, cosine
similarity).

Otherwise compute two scores:

**(a) Position score — point-to-polyline distance.**
For each point in `userStroke`, find the nearest distance to any segment of `expected`;
take the mean. Do the symmetric computation (each `expected` point → nearest distance to
`userStroke`) and take the mean of both directions (Chamfer-like symmetric distance).
Normalize isn't needed — both are in 1000-space units.

```
meanDist = ( Σ minDist(userStroke[i], expected)
           + Σ minDist(expected[j], userStroke) )
         / (len(userStroke) + len(expected))
```

A low `meanDist` means the user's stroke sits where the expected stroke sits.

**(b) Direction score — local tangent agreement.**
Sample tangents (direction vectors) at corresponding arc-length positions along both
polylines. Cosine similarity, averaged. This rejects strokes that are in the right place
but drawn backwards, or with the wrong overall sweep.

```
directionScore = mean( cos(angle(userTangent[k], expectedTangent[k])) )
```

A stroke drawn the wrong way scores low (e.g. a horizontal drawn right-to-left when it
should be left-to-right).

### Step 3 — Classify

Combine into a verdict using thresholds (constants in one place — see *Tuning*):

| Condition | Verdict | Action |
|-----------|---------|--------|
| `meanDist ≤ ACCEPT_DIST` **and** `directionScore ≥ ACCEPT_DIR` | ✅ **Accept** | Advance expected index; turn the stroke solid (correct color). |
| `meanDist ≤ SLOPPY_DIST` **and** `directionScore ≥ SLOPPY_DIR` | 🟡 **Accept (sloppy)** | Advance, but mark the stroke with a "close enough" tint; counts as correct for SRS but logs `drawnCorrectly` nuance. |
| `meanDist` small but **wrong stroke index** would've matched better | 🔴 **Wrong stroke / out of order** | Reject; briefly flash the *correct* next stroke's faint outline + median arrow. |
| Otherwise | 🔴 **Reject** | Reject; flash correct path; let the user retry. |

"Wrong stroke index would've matched better": also compute the position score against a
few *other* stroke medians (e.g. the next 1–2 expected). If another stroke matches
markedly better, the user drew the right *kind* of stroke but in the wrong order — give
the specific "out of order" feedback rather than a generic reject.

### Step 4 — Completion

When the expected index passes the last stroke, the character is **complete**. The card's
outcome maps to an SM-2 grade — this table is the **authoritative definition**, shared
with the SRS engine in `06`:

| Card outcome | SM-2 grade |
|---|---|
| every stroke clean-accepted, no hints | 5 |
| ≥1 sloppy accept, no hints | 4 |
| any hint ("show me") used | 3 |
| any single stroke rejected 3+ times, but card completed | 2 |
| card abandoned / majority of strokes failed | 1 |

- Grades **< 3 are SM-2 lapses** (the card re-enters `RELEARNING`) even though the user
  eventually completed the drawing — needing that much correction *is* forgetting.
- `drawnCorrectly` (logged in `ReviewLog`) = grade ≥ 4.
- Hand the result + timing back to the ViewModel → SRS update (see `06`).

## Tuning

All thresholds live in a single `object GradingConfig` so they're tunable in one place and
testable in isolation. Starting points (in 1000-space units; to be tuned empirically):

```kotlin
object GradingConfig {
    const val RDP_EPSILON        = 2.0
    const val RESAMPLE_STEP      = 10.0
    const val ACCEPT_DIST        = 45.0   // mean normalized distance
    const val ACCEPT_DIR         = 0.6    // mean cosine similarity
    const val SLOPPY_DIST        = 70.0
    const val SLOPPY_DIR         = 0.4
    const val MAX_SLOPPY_BEFORE_FAIL = 2
    const val LOOKAHEAD_STROKES  = 2      // how many future strokes to disambiguate order
    const val LEN_RATIO_MIN      = 0.5    // length guard band
    const val LEN_RATIO_MAX      = 2.0
    const val SHORT_STROKE_LEN   = 60.0   // below: dot-stroke path (position + endpoint vector)
    const val MIN_STROKE_UNITS   = 15.0   // below (and < ~30ms): accidental contact, ignore
}
```

### Golden test set

To tune without guessing, build a small **recorded-stroke corpus**, seeded during the
Phase 0 prototype (see `08`), whose character set deliberately includes dots, hooks, and
crossings (e.g. 一 人 我 火 心 小 乙):

- A `test/resources/strokes/<character>/` folder with several hand-drawn (or
  mouse-drawn-in-an-emulator) attempts per character: clean, sloppy, wrong-order, wrong-
  stroke. Each is a saved polyline + the expected verdict.
- A unit test replays each through the grader and asserts the verdict. Tuning means
  adjusting thresholds until the corpus passes. This makes grading changes measurable
  rather than subjective.

The corpus ships with the test code (no PII — they're geometric paths).

## Failure & recovery UX

- On reject, the user's just-drawn stroke **fades away** (not left as permanent ink), so
  the canvas stays uncluttered for a retry.
- After 2 consecutive rejects on the same stroke, offer a **"show me"** button that plays
  the demo for the current stroke and lifts grading pressure (next attempt accepted more
  leniently, marked as hinted).
- A persistent **undo** clears the last accepted stroke (in case of a misfire).

## Performance

- All grading is O(points × segments) per stroke — trivially fast for a single character
  (hundreds of points). Runs synchronously on pointer-up; no threading needed.
- Rendering uses Compose `Canvas`; a state change redraws the whole canvas, which is
  trivially cheap at this scale (a few dozen filled paths + one live polyline).
- No allocations in the draw loop beyond the per-stroke polyline; reused buffers where it
  matters (likely premature — measure first).

## Accessibility

- Drawing is inherently a motor task; the engine must be **forgiving** (hence the sloppy
  tier and hint system) so it's usable for people with less motor precision or using a
  finger rather than a stylus.
- Demo mode + audio (TTS) mean a character can be *shown and heard* even without drawing.
- A future "recognition-only" mode (skip drawing, just reveal) is a reasonable
  accessibility fallback — noted, not in MVP.

## Open questions

- [ ] Outline-clip demo vs thick-median demo — decide during the Phase 0 prototype (start
      with thick-median; it may be good enough to keep for MVP).
- [ ] Whether to weight early strokes more heavily in the final `drawnCorrectly` decision
      (early strokes anchor the character). Lean: no, keep it uniform for MVP simplicity.
- [ ] Direction score: compare at matched arc-length positions, or use a stroke-level
      endpoint-vector check? Start with arc-length sampling; revisit if tuning is hard.
