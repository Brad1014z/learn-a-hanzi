# 11 — Family Prototype (the son's mini-spec)

> **Status:** ACCEPTED (2026-07-05)
> Phase 0 (`08`) is a **father-son build**: one app, two workstreams. Dad (+ AI) builds
> the grading engine — the hard, testable core. The 12-year-old co-creator (+ AI) owns
> **game feel**. This spec is his: it stages the work into sessions that each end with
> something visible on a real phone, because the meta-goal is as important as the app —
> getting hooked on building things with AI.

## Why this exists (the meta-goals)

The prototype is a gateway. Each session should quietly teach:

| Meta-goal | How it shows up |
|-----------|-----------------|
| **Vibe coding / prompting** | He describes what he wants; Claude Code writes Kotlin; he sees his words become software. Iterating on a prompt *is* the skill. |
| **Reading code** | Once per session: open one file the AI wrote and walk through it out loud — what's a value, a function, state, an event. No lectures; just "what did we build?" |
| **CS basics** | Coordinates and geometry (the canvas is math!), state machines (stroke verdicts), data (characters as JSON). |
| **Game design** | Juice, feedback loops, difficulty, celebration timing. His decisions in `10` (naming!) are real product decisions. |
| **Shipping & showing off** | The app runs on *his* phone. Demo day with friends is a milestone, not an afterthought. |

**The one rule that matters:** every session ends with something he can *see and touch*.
If a session risks ending in plumbing, dad pre-bakes the plumbing beforehand.

## Roles

- **Dad:** engine workstream (`08` Phase 0 list), environment setup, pre-bake before S1,
  PR review of the son's branches.
  *Pre-bake status (2026-07-06):* it went further than the original "skeleton" — the
  working app already has the grid (with meanings), demo animation, full quiz grading,
  hint/undo/guide, placeholder verdict sounds with a toggle, and CI producing a test APK.
  That's fine: it shifts the sessions from "build these from nothing" to **"make these
  yours"** — every game-feel surface carries a `TODO(son, S1..S5)` marker in the code.
- **Son:** game-feel workstream below; app name + icon; all naming in `10`; the ideas
  backlog. Works on his own branch; his changes reach `main` by PR that dad reviews —
  review culture from day one, gently.
- **Both:** weekly playtest ("family demo day") — play the current build, pick the next
  session's target together.

## The sessions

Each is one sitting (~60–90 min), each ends with a demo-able win. Order matters — every
session builds on the previous one's visible thing.

### S1 — "It's alive"
A character (his pick — 火 is a good first: 4 strokes, looks like fire) animates
stroke-by-stroke on screen, in colors he chose, with a replay button and **confetti**
when it finishes. He names the app (v0 name, changeable).
*Learns:* prompting loop, run-on-device, what a Composable is (one walkthrough).
*Pre-baked by dad:* the demo animation already runs — S1 is making it *his*: colors
(`PracticeColors` is waiting), the app name, and the first pass at completion confetti.

### S2 — "Trace it"
Finger drawing: ink follows his finger over a faint version of the character. Brush
color picker. No grading yet — drawing just *works* and feels good.
*Learns:* touch events, canvas coordinates (why is y upside down? great 5-minute story —
see the Y-flip in `02`).

### S3 — "The Shelf"
The collection is born: every character traced so far appears in a grid; tap one to
replay its animation. This is the first version of the real product's Collection screen
(`07`).
*Learns:* lists and state — where "which characters have I done" lives.

### S4 — "It judges you"
Dad's grading engine already judges — S4 is **designing the feedback**: replace the
placeholder verdict sounds (synthesized WAVs in `res/raw`, wired via `SoundPlayer` — react
to them, then make better ones), pick the accept/reject colors, add the reject shake and
haptics, decide how mean or kind the retry moment feels. This is real game design on a
real grading system.
*Learns:* thresholds and tuning (`GradingConfig` is sliders for feelings — reject
messages already show the raw scores), the sloppy tier as a design choice.

### S5 — "Boss + share"
A tiny session loop: trace 3 characters → boss character → confetti + XP counter →
done screen. APK installed on his phone. **Demo day with friends.**
*Learns:* what a build/APK is; the difference between "works on the emulator" and
"works in my hand".

### Beyond — his backlog
A running list he owns (in-repo `docs/family-backlog.md`, his words): sounds, icon
ideas, characters he wants, arcade ideas for `10`, "what my friends said". Family demo
day pulls the next session's goal from it.

## Tooling

- **Claude Code** for both workstreams — he prompts, reads the diff with dad, accepts.
- **Android Studio** with **Live Edit** for fast Compose iteration; project pre-configured
  so he never fights Gradle.
- **Physical phone** over wireless debugging — seeing it on a real device (his device)
  is the point; the emulator is the fallback.
- **Git:** his own branch; small commits with his own messages; PRs to `main` reviewed
  by dad. GitHub's ToS requires age 13 for an account, so until then his work is
  attributed via `Co-authored-by` on commits and in-app credits ("game feel by ___");
  at 13 he can fork to his own account and own his history.

## What this workstream is *not*

- Not a separate toy — S3/S4/S5 outputs are the seeds of the real Collection screen,
  verdict feedback, and quest chest (`07`, `10`). His code is product code (reviewed,
  like anyone's).
- Not a curriculum for him to complete — if a session's plan bores him and he'd rather
  make the confetti purple and add a fart sound to rejects, that *is* game-feel work.
  The backlog bends to the kid, not the kid to the backlog.

## Success criteria

1. He asks — unprompted — when the next session is.
2. He demos the app to someone without being asked (this is also Phase 0's DoD in `08`).
3. By S5 he can explain, in his own words, what the AI does, what the code roughly does,
   and one thing he'd design differently.
