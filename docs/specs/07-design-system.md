# 07 — Design System

> **Status:** ACCEPTED (reviewed 2026-07-05)
> Theme tokens, core screens, and the key user flows. Focus is on structure and behavior;
> pixel-perfect visuals are finalized at implementation with reference mockups.

## Theme & tokens (Material 3)

A single source of truth for color, type, and shape — implemented in `:core:ui`, consumed
by every feature.

### Color
- **Material 3 color roles** (primary, secondary, tertiary, surface, background, error, and
  their `on-*` counterparts).
- **Dark mode first-class**: both light and dark schemes defined; app follows system by
  default, with a manual override in Settings.
- **Dynamic color (Material You)** opt-in on Android 12+; off by default for brand
  consistency, on if the user enables it.
- **Semantic colors** beyond M3 roles, used by the practice engine:
  - `ink` — the user's drawn stroke color.
  - `strokeCorrect` — accepted stroke tint (green family).
  - `strokeSloppy` — "close enough" tint (amber).
  - `strokeWrong` — reject flash (red).
  - `guide` — rice-grid + faint target character (low-emphasis).
  - `toneMark` — pinyin tone-number/tone-mark accent.

### Typography
- Chinese characters in a **large, legible sans** (system default Noto Sans CJK; consider
  bundling a variable font only if system rendering is poor on target devices).
- Pinyin in a slightly smaller weight, with **tone marks visually emphasized** (color or
  weight) — tones are part of the word.
- English definitions/examples at comfortable body size with generous line height.

### Shape & elevation
- Generous corner radii (M3 "large" shape scale) for cards and the practice canvas frame.
- Low elevation; rely on color/spacing for separation. Calm, not glossy.

### Motion
- Stroke reveal animations are smooth and short (≤ ~400ms per stroke in demo mode).
- Feedback flashes are quick (≈ 150–250ms) so the user keeps their rhythm.
- No gratuitous screen transitions.

## Core screens

### 1. Home / Dashboard
- **Today's reviews** count card (prominent, tappable → Review screen).
- **New characters** available today (tappable → starts the "learn a new char" flow).
- **Streak / progress** summary (characters mastered, days active) — honest, not pressure.
- Quick entry to Browse and Settings.

### 2. Character Detail
- Large character display (rendered from stroke outlines).
- Pinyin (tone-marked) + audio button (TTS).
- Short English definition + decomposition/radical (+ etymology hint if present).
- **Example phrase**: one short word/sentence containing the character, with translation.
- Buttons: **Practice** (start quiz), **Show stroke order** (demo).

### 3. Practice (stroke quiz) — the centerpiece
- Full-bleed **Canvas** with the rice-grid guide; minimal chrome.
- Top bar: target character (faint), pinyin, audio, progress (stroke x of n).
- **Tracing guide** (faint full character on the canvas): defaults **on during
  first-learn**, **off during review** — scaffolding that fades as recall takes over;
  overridable in Settings (see `05`).
- Controls: **Hint** (show next stroke), **Undo**, **Replay demo**, **Exit**.
- Drawing input → live ink; on pointer-up, grading feedback (color + optional haptic).
- On completion: success state, SRS grade recorded, "Next" / "Done".

### 4. Review (SRS queue)
- Presents due characters one at a time in the practice canvas (same engine as Practice,
  but driven by the review queue, no new-character intro step, tracing guide off).
- Progress indicator (x of n due today); session summary at the end (retention %, time).

### 5. Browse
- Search by character, pinyin, English, or radical.
- Level/group browsing (HSK 1 list, radical groups).
- Tap any character → Character Detail. (Browse practice doesn't consume the daily cap.)

### 6. Progress (later / MVP-minimal)
- Characters mastered, review calendar heatmap, retention over time.
- MVP can ship a minimal version (mastered count + last-7-days activity).

### 7. Settings
- Daily new-character cap, theme (system/light/dark), dynamic color toggle, TTS engine
  picker + preview, sound/haptics toggles, credits (data sources + licenses), reset progress.

## Key user flows

### Flow A — Learn a new character (the core learning loop)
1. Home → tap "New characters" → curriculum serves the next unlocked character.
2. **Intro:** Character Detail content shown (meaning, pinyin, audio, example).
3. **Demo:** app animates correct stroke order (play once, allow replay, slow-mo).
4. **Quiz:** user draws each stroke; engine grades per `05`. Hint/undo available.
5. **First review:** the card re-enters the tail of the current session's queue (first
   learning step — see `06`), then comes due again the next day.
6. → Returns to Home or proceeds to the next new character (up to daily cap).

### Flow B — Daily review
1. Home → tap "Today's reviews" → Review screen.
2. Each due card presented in the practice canvas; user draws; graded.
3. SRS engine updates interval/ease/dueAt; appends ReviewLog.
4. On queue empty: session summary; Home updates.

### Flow C — Free exploration
1. Browse → search/tap a character → Detail → optional Practice (free, doesn't affect
   guided track or daily cap).

## Interaction details

- **Audio button** anywhere a character/word/sentence appears → TTS speaks it. Debounced
  so rapid taps don't queue overlapping utterances.
- **Polyphonic characters (多音字):** for a character whose pinyin array has multiple
  readings (e.g. 了, 长), TTS on the bare glyph may pick a reading that contradicts the
  pinyin on screen — so the character's audio button speaks it **inside its example word**
  instead (see `01`). Word/sentence audio is unaffected.
- **Haptics** on grading verdicts (light tick on accept, stronger on reject) — toggleable.
- **Offline indicators**: none needed in normal use (always offline-first); a one-time
  hint if no Mandarin TTS engine is installed.
- **Back/gesture nav**: practice exits with a confirm if a stroke is mid-draw.

## Accessibility

- All interactive elements at least 48dp target; the canvas is far larger.
- Content text respects font scaling; the large character display scales within the canvas.
- Demo + audio provide a non-drawing path to encounter a character.
- Color is never the *only* signal for verdicts — pair with shape/icon/haptic (e.g., wrong
  stroke also briefly shows the correct path).

## Visual identity (to finalize)

- Name/wordmark, app icon (an adaptive icon — a single character or stroke motif).
- Onboarding: a single short screen explaining the writing-first philosophy + TTS prompt.
  No multi-step wizard; constitution favors getting into the app fast.

## Open questions

- [ ] Bundle a CJK font or rely on system Noto? Decide after testing on a couple of
      emulator/API levels.
- [ ] Rice-grid (米字格) vs. simpler box guide — rice grid is more authentic and useful for
      proportion; lean yes.
- [ ] Tone mark emphasis: color vs. weight. Try both at implementation.
