# 05 — Stroke Engine

> **Status:** DRAFT
> The technically hardest part of the app. This spec describes how the app renders strokes
> and **grades the user's freehand input** against a known target character — entirely in
> native Kotlin on a Compose `Canvas`, no WebView, no third-party library.
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

- All stored geometry is normalized to a **1000×1000** box (the ingest tool normalizes
  from make-me-a-hanzi's source space — historically 1024×1024; ⚠ verify, see `02`).
- The drawing `Canvas` maps the 1000×1000 box to its pixel size with a single scale +
  translate. Touch input is mapped *back* into 1000-space before grading, so thresholds
  are defined in normalized units and are device-independent.
- A **guide box** (米字格 / rice grid) is drawn behind the character as a writing aid.

## Rendering (shared)

- **Completed strokes / outline:** parse `pathData` (SVG `M/L/Q/C/Z`) into an
  `android.graphics.Path` via `PathParser` (androidx) and fill it. Drawn in the on-screen
  "ink" color.
- **Demo animation:** for stroke `i`, reveal it by drawing the outline **up to a progress
  fraction** along its `median` — i.e., animate a "growing" stroke by clipping/filling the
  outline region near the median prefix. A simpler MVP approach: draw the median as a
  thick rounded stroke up to the progress fraction (less pretty, far simpler); upgrade to
  outline-clipping in Phase 2.
- **Faint target:** in quiz mode, optionally show all strokes at low opacity as a guide
  the user traces (a setting; default off so the user draws from memory).

## Quiz mode — grading algorithm

Goal: as the user lifts their finger, decide whether the stroke they just drew is the
**correct next stroke** (right shape, right position, right order) and respond with
feedback. We grade **one stroke at a time** and advance the expected-stroke index on
acceptance.

### Step 1 — Capture & clean the input

1. Collect raw touch points `(x,y)` between pointer-down and pointer-up, mapped into
   1000-space.
2. **Simplify** with Ramer–Douglas–Peucker (RDP) at a small epsilon (e.g. 2.0 units) to
   drop redundant points.
3. **Resample** to a fixed point density (e.g. one point every ~10 units along arc length)
   so stroke comparisons aren't biased by drawing speed or point count. Call the result
   `userStroke`: an ordered polyline.

### Step 2 — Match against the expected stroke

Let `expected` be the `median` polyline of the **current expected stroke index** (0-based,
advancing only on accept). Both `userStroke` and `expected` are polylines in 1000-space.

Compute two scores:

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

When the expected index passes the last stroke, the character is **complete**:
- Mark the practice card `drawnCorrectly` if all strokes were Accept or Accept(sloppy)
  with at most N sloppy/retry events (tunable).
- Hand the result + timing back to the ViewModel → SRS grade (see `06`).

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
}
```

### Golden test set

To tune without guessing, build a small **recorded-stroke corpus**:

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
- Rendering uses Compose `Canvas`; we invalidate only the affected region on each stroke.
- No allocations in the draw loop beyond the per-stroke polyline; reused buffers where it
  matters (likely premature — measure first).

## Accessibility

- Drawing is inherently a motor task; the engine must be **forgiving** (hence the sloppy
  tier and hint system) so it's usable for people with less motor precision or using a
  finger rather than a stylus.
- Demo mode + audio (TTS) mean a character can be *shown and heard* even without drawing.
- A future "recognition-only" mode (skip drawing, just reveal) is a reasonable
  accessibility fallback — noted, not in MVP.

## Phase 2 addendum — Arcade reuse

> The Arcade game modes (spec `09`) reuse this grader **unchanged**. This section only adds
> a hook so the game layer can react to grading — **no algorithm change.**

- **Per-stroke verdict callback.** The grader already produces a verdict (Accept / Accept-
  sloppy / Reject) on each pointer-up. Expose it as a callback (`onStrokeVerdict`) so the
  Arcade combo/scoring layer can react in real time (break a combo on reject, award a clean
  bonus on a non-sloppy character, play a punchier haptic).
- **Stateless, mode-agnostic.** The grader itself stays pure: it knows the target character
  and the input, nothing about which "mode" invoked it. The caller (practice screen vs.
  arcade screen) decides what to *do* with each verdict.
- **Session framing.** For Arcade, the caller drives the sequence of target characters
  (per `09`'s pool rules) and wraps each in its own grader turn. The grader's contract per
  character is identical to practice: draw strokes → verdicts → completion.
- **Performance under speed.** Sprint/Speed Write push stroke throughput higher than
  practice mode, but per-stroke grading remains O(points × segments) and trivially fast
  (see *Performance*). No special-casing needed.

## Open questions

- [ ] Outline-clip demo vs thick-median demo — decide after seeing real `pathData`.
- [ ] Whether to weight early strokes more heavily in the final `drawnCorrectly` decision
      (early strokes anchor the character). Lean: no, keep it uniform for MVP simplicity.
- [ ] Direction score: compare at matched arc-length positions, or use a stroke-level
      endpoint-vector check? Start with arc-length sampling; revisit if tuning is hard.
