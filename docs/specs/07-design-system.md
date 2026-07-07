# 07 — Design System

> **Status:** ACCEPTED (reviewed 2026-07-05; amended 2026-07-05 — play layer, see `10`)
> Theme tokens, core screens, and the key user flows. Focus is on structure and behavior;
> pixel-perfect visuals are finalized at implementation with reference mockups.
>
> **Tone: all-ages playful.** The app is a game (see `00`/`10`) — generous, juicy
> feedback everywhere *except* during the act of writing, which stays focused and calm.
> Playful never means childish: type, color, and copy work for a 12-year-old and a
> 40-year-old at once.

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
- **Celebration moments** (stroke-set complete, rank-up, world unlock, chest opening) may
  be big — confetti, sound, haptics — but short (≤ ~1.5s) and always skippable by tap.
  Celebrations happen **between** writing moments, never during one.
- No gratuitous screen transitions.

## Core screens

### 1. Home — the Quest Hub
- **Today's quest** card (prominent): warm-up → reviews → new characters → boss stroke →
  chest, with progress shown as quest steps (see `04`). Tap to start/continue.
- **Daily challenge** entry (today's shared puzzle + its share card once done — see `10`).
- **Current world** strip: world name, mastery progress toward the next world unlock.
- **Collection** teaser (latest characters + any dimmed ones asking for practice).
- **Streak / XP** summary — days played and learner level; honest, pauses gracefully.
- Quick entry to Browse, Collection, and Settings.

### 2. Character Detail
- Large character display (rendered from stroke outlines).
- Pinyin (tone-marked) + audio button (TTS).
- Short English definition + decomposition/radical (+ etymology hint if present).
- **Example phrase**: one short word/sentence containing the character, with translation.
- Buttons: **Practice** (start quiz), **Show stroke order** (demo).

### 3. Practice (stroke quiz) — the centerpiece
- Full-bleed **Canvas** with the rice-grid guide; minimal chrome.
- Top bar: target character (faint), progress (stroke x of n), audio.
- **Meaning line** under the top bar: tone-marked pinyin + short English gloss
  ("yī · one") — the character is always more than a shape, even mid-quiz. The gloss is
  the first clause of the full definition (definitions are `;`-separated, see `02`).
- **Tracing guide** (faint full character on the canvas): defaults **on during
  first-learn**, **off during review** — scaffolding that fades as recall takes over;
  overridable in Settings (see `05`).
- Controls: **Hint** (show next stroke), **Undo**, **Replay demo**, **Exit**.
- Drawing input → live ink; on pointer-up, grading feedback (color + sound + optional
  haptic).
- On completion: success state showing character · pinyin · meaning (one last
  reinforcement), SRS grade recorded, "Next" / "Done".

### 4. Review (SRS queue)
- Presents due characters one at a time in the practice canvas (same engine as Practice,
  but driven by the review queue, no new-character intro step, tracing guide off).
- Progress indicator (x of n due today); session summary at the end (retention %, time).

### 5. Browse
- Search by character, pinyin, English, or radical.
- Level/group browsing (HSK 1 list, radical groups).
- Tap any character → Character Detail. (Browse practice doesn't consume the daily cap.)

### 6. Collection — the trophy room (MVP: simple grid)
- Every encountered character displayed as a collection tile: silhouette → Bronze →
  Silver → Gold (ranks per `04`); lapsed characters shown dimmed with a gentle "practice
  me" affordance.
- Organized by world; tap a tile → Character Detail (with replay-the-strokes animation).
- Stats live here too: characters mastered, review calendar heatmap, retention over time
  (MVP: mastered count + last-7-days activity).
- **Badge shelf** (Phase 3): earned badges with transparent criteria — tap any badge to
  see exactly what earned it or what's left (`10`). Unearned badges show as goals, never
  as guilt.
- MVP ships the simple grid + ranks; art/personality per character is a Phase 3 pass.

### 7. Settings
- Daily new-character cap, theme (system/light/dark), dynamic color toggle, TTS engine
  picker + preview, sound/haptics toggles, credits (data sources + licenses), reset progress.
- **Account** (Phase 4): sign in/out with Google, re-roll display name, pick avatar,
  rotate friend code, delete account. Signed-out state is a calm single row, not a banner.

### 8. Profile & Friends (Phase 4)
- My card: generated name, avatar, weekly XP, badge count; **friend code** with share/QR.
- Friends list (mutual only): name, avatar, weekly XP (ceilinged); remove silently.
- Challenges: incoming (accept/ignore), active (play), finished (results + preset
  reactions 👏 🔥 😮 🤝 — the only inter-user signal; no free text anywhere, per `00`/`12`).
- Weekly friends board resets Monday; no relegation, no loss-framing.

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

### Flow D — Daily challenge & share
1. Home → "Daily challenge" → today's character puzzle (same for everyone, derived
   offline from the date — see `10`).
2. User writes it from memory; per-stroke verdicts recorded.
3. Result card: spoiler-free emoji grid of stroke verdicts (🟩🟨🟥) + day number.
4. **Share** via the OS share sheet (no server, no account); or dismiss. Done for the day.

### Flow E — Friend challenge (Phase 4, signed-in)
1. Profile & Friends → pick a friend → "Challenge" → daily duel or set duel (`12`).
2. Friend gets it on their next app open (no push nagging); accepts or lets it expire —
   both consequence-free.
3. Both play the same puzzle/set; results appear for both; optional preset reaction.
4. Weekly friends board updates with ceilinged XP.

### Flow F — Sign-in (Phase 4, always optional)
1. Settings row, or a one-line card after a completed quest ("Back up your progress?").
2. Google one-tap → generated name + avatar picker → done. Dismissing the card mutes it.

## Interaction details

- **Audio button** anywhere a character/word/sentence appears → TTS speaks it. Debounced
  so rapid taps don't queue overlapping utterances.
- **Polyphonic characters (多音字):** for a character whose pinyin array has multiple
  readings (e.g. 了, 长), TTS on the bare glyph may pick a reading that contradicts the
  pinyin on screen — so the character's audio button speaks it **inside its example word**
  instead (see `01`). Word/sentence audio is unaffected.
- **Verdict sound effects** on grading (bright short ding on accept, **gentle** low tone
  on reject — never harsh, per `00`'s feedback-that-teaches principle — and an ascending
  sparkle on character completion) — toggleable, independent of TTS. Phase 0 ships
  synthesized placeholders; the final sounds are the co-designer's call (`11`, S4).
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
