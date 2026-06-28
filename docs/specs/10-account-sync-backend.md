# 10 — Account, Sync & Backend

> **Status:** DRAFT · **Phase 2b/2c**
> The optional cloud layer: authentication, score/XP sync, and the serverless backend that
> powers anonymous leagues. **Nothing here is required for the MVP** (`Phase 1`), and the app
> remains fully usable without an account (constitution principle #4).
>
> **Vendor:** the plan recommends **Firebase** (Auth + Firestore + Cloud Functions) as the
> primary target; **Supabase** (Postgres + Edge Functions) is the open-source alternative.
> Both are accessed through the same in-app interfaces, so the choice is reversible.

## Scope

- **In scope (Phase 2b/2c):** optional Google sign-in, score/XP submission, daily
  leaderboard, weekly anonymous leagues.
- **Out of scope:** email/password auth (no password surface to maintain), friends/social
  graph, chat, real-time multiplayer, paid tiers.

## Authentication

- **Method:** Google Sign-In (one-tap). Provider-managed; no passwords we own.
- **Required for:** submitting scores/XP, viewing live league standings.
- **NOT required for:** anything in the learning path, playing games locally, viewing local
  high scores.
- **First sign-in:** creates an anonymous profile server-side with an auto-generated handle
  (see leagues in `09`). The user can re-roll the handle anytime.
- **Sign-out / account deletion:** always available in Settings. Deletion must purge the
  user's server-side data (league membership, submissions, profile) — a Cloud Function
  endpoint that hard-deletes within N days.

## Backend (serverless)

### Recommended: Firebase
- **Firebase Auth** — Google provider.
- **Cloud Firestore** — `profiles`, `submissions`, `leagueMemberships`, `dailyLeaderboard`
  collections. Security rules enforce: a user writes only their own profile + submissions;
  leagues/leaderboards are read-only via the client or materialized by functions.
- **Cloud Functions** — scheduled + triggered:
  - **Weekly league shuffle** (cron, Mon 00:00 UTC): assign each active user to a fresh
    ~12-person league for the week (see `09`).
  - **Score validation** (on submission write): bounds-check (timing, theoretical max) and
    compute XP, update the user's weekly + daily aggregates.
  - **Daily leaderboard reset** (cron, 00:00 UTC): rotate the day's challenge leaderboard.
- **Why:** trivial Google auth integration, near-zero ops, generous free tier for a
  portfolio-scale user base, mature Android SDK.

### Alternative: Supabase
- **Supabase Auth** (Google provider) + **Postgres** + **Edge Functions** (Deno) + **Row
  Level Security**.
- Same logical schema; RLS replaces Firestore security rules; a `pg_cron` job does the weekly
  shuffle; an Edge Function validates scores.
- **Why prefer it:** open-source, self-hostable, relational (easier aggregate leaderboard
  queries), no Google-platform lock-in. **Cost:** more glue code, slightly steeper Android
  integration.

### Why serverless over a VPS
Zero ops for a personal/portfolio project — no DB backups, security patches, or scaling to
babysit. The tradeoff (vendor lock-in) is mitigated by the interface boundary below.

## Sync protocol & outbox

The app is **offline-first**. Writes to the cloud go through an **outbox**, never blocking
the UI or the game.

```
Game finishes
  → Room: insert GameSession + GameScore + LocalHighScore (immediate, local)
  → if signed in & online: enqueue PendingXpSync(outbox row) and trigger drain
  → if offline/unsigned: PendingXpSync stays queued; drained on reconnect/sign-in
```

- **Drain:** a coroutine worker (`WorkManager`) periodically (and on connectivity change)
  POSTs pending submissions to the validation function, retries with backoff, and deletes
  the outbox row on confirmed success.
- **Idempotency:** each submission carries a client-generated UUID; the server dedupes on
  it so retries can't double-count XP.
- **Conflict:** the server is the source of truth for league/leaderboard standings; the
  client only submits and reads. No merge logic needed.
- **Outbox cap:** if submissions pile up beyond a sane limit (e.g. user offline for weeks),
  older low-XP entries are coalesced/dropped with a note — documented honestly.

## Data model (server-side, mirrored locally — see `03`)

Server collections/tables (Firebase collections or Supabase tables):

- `profiles` — `{ uid, handle, createdAt, leagueTier }`
- `submissions` — `{ uid, submissionId(uuid), mode, characterSetId, score, xp, startedAt,
  durationMs, events[], validated, createdAt }`
- `weeklyAggregates` — `{ uid, weekStart, totalXp }` (materialized by the validation fn)
- `leagueMemberships` — `{ uid, weekStart, leagueId, tier }`
- `dailyLeaderboard/{date}` — `{ uid, bestScore, bestXp }`

Local Room mirrors (read-only-ish caches + the outbox) are defined in `03` Phase 2 tables:
`Profile`, `GameSession`, `GameScore`, `LocalHighScore`, `PendingXpSync`, `LeagueMembership`.

## Scoring validation (server-side)

On a `submissions` write, the validation Cloud Function / Edge Function:

1. Loads the `GameConfig` theoretical-max formula for `mode` + the reported character set.
2. Checks `durationMs` is within `[minPlausible, maxPlausible]` for the mode.
3. Checks `score ≤ theoreticalMax` and `events` are monotonic + within the session window.
4. Rate-limits: ≤ N submissions per user per day per mode (e.g. 50) to cap farming.
5. On pass: marks `validated`, adds `xp` to `weeklyAggregates` + `dailyLeaderboard` (best-of).
   On fail: marks `validated=false`, drops silently (no client error, no ban).

This is **bounds validation, not re-grading** — we never replay the strokes. Honest and cheap.

## Privacy stance

- **Anonymous by default.** Handles are auto-generated; we never expose real names or emails
  in leagues/leaderboards. Email from Google sign-in is stored only for account identity,
  not displayed.
- **Minimal data.** We collect: auth uid, generated handle, game submissions, league
  membership. **No analytics, no advertising SDKs, no third-party trackers** (constitution).
- **Data retention:** submissions kept long enough for league history (e.g. rolling 90 days),
  then aggregated/anonymized. Account deletion hard-deletes within N days.
- **A privacy policy** is required before any public release listing the above.

## Architecture boundaries (no lock-in)

All cloud access goes through interfaces in `:core:account` and `:core:sync`, defined in
`:core:domain` style (see `06` amendments). The Firebase/Supabase implementation lives behind
those interfaces, so:

- The feature layer (`:feature:arcade`) calls `ScoreRepository.submit(...)` and
  `LeagueRepository.currentStandings()`, never Firebase/Supabase types directly.
- A `FakeScoreRepository` makes Arcade UI tests fully offline (no emulator needed).
- Swapping Firebase → Supabase (or a self-hosted backend later) touches only the impl module.

## Failure modes

| Scenario | Behavior |
|----------|----------|
| Offline at game end | Save locally; queue in outbox; show local high score; no error. |
| Sign-in fails | Game still completes locally; outbox drains on next successful sign-in. |
| Submission rejected (validation) | Silently dropped; local score remains; no user-facing error. |
| League fetch fails / offline | Show last-cached standings with "offline" indicator. |
| Backend fully down | App is identical to the no-account experience. The constitution holds. |

## Open questions

- [ ] Firebase vs Supabase — confirm before Phase 2b implementation. (Plan recommends Firebase.)
- [ ] Submission rate-limit thresholds — tune after seeing real session cadence.
- [ ] Whether to expose "seasons" (monthly meta-leagues) atop weekly leagues — deferred.
- [ ] Free-tier cost ceiling: estimate MAU the Firebase free tier supports before deciding to
      cap or pay. ⚠ verify current Firebase pricing before public release.
