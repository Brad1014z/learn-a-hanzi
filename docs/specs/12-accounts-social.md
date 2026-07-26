# 12 — Accounts, Sync & Social

> **Status:** ACCEPTED (2026-07-06; rescheduled 2026-07-09 — now roadmap milestone
> **M4**, see `08`; amended 2026-07-12 — **M4 ships the free-tier slice**: sign-in,
> sync/backup, friends by code, weekly board. Each client writes its own *ceilinged*
> weekly XP doc (rules-bounded) instead of a Cloud Function aggregating — Functions
> require the paid Blaze plan, so **challenges/duels + server-side validation +
> account-deletion purge move to M4.5**. Firestore rules live in
> `firebase/firestore.rules`; console setup in `firebase/README.md`.)
> The optional cloud layer: Google sign-in, progress sync, friends challenges, badge
> backup, and the audio-generation pipeline's service notes. **Nothing here is required
> to learn or play** — the app is fully functional offline and signed-out, forever
> (constitution principles 1 and 4, amended 2026-07-06).
>
> Lineage: adapts the closed PR #1 backend design (auth, outbox, bounds validation) to
> the friends-challenges shape decided 2026-07-06; PR #1's anonymous leagues remain
> archived as a possible later addition.

## Scope

- **In:** optional Google sign-in; learning-progress sync/backup; friends by mutual code;
  async score challenges; weekly friends XP board; badge backup; account deletion.
- **Out (banned by constitution):** chat/DMs or any free-text channel between users,
  stranger discovery/search, contact-list access, follower counts, real-time multiplayer.
- **Out (archived for later):** anonymous weekly leagues (PR #1 design), global
  leaderboards.

## Authentication

- **Method:** Google Sign-In via Credential Manager → Firebase Auth. No email/password
  (no password surface to own), no other providers at launch.
- **Required for:** sync, friends, challenges, badge backup. **Not required for:**
  everything else in the app.
- **Sign-in entry points** are calm (`07`): a Settings row and a one-line card after a
  completed quest ("Want your progress backed up?"). Never a blocking screen, never a
  nag, never repeated after dismissal.
- **Sign-out:** local data is untouched; the device keeps working as a signed-out device.
- **Account deletion:** always available in Settings; a Cloud Function hard-deletes all
  server-side data within 30 days. Local data survives unless the user also resets.

## Identity — generated, safe, no free text

- Display identity = **generated two-word name** (e.g. "Swift Otter") with unlimited
  re-rolls + a **preset avatar** picked from a bundled set. No custom text names, no
  custom images — this closes the entire name-moderation problem for an all-ages app.
- The Google account's email/real name are **never** shown to other users and never
  leave the profile record.

## Friends — mutual codes only

- Each user has a short **friend code** (and QR / share-link form). Codes are
  rotatable; sharing one is the only way to connect. No search, no suggestions, no
  contact import (constitution: minimal permissions).
- A connection requires **both** sides: A shares a code, B enters it, A confirms.
  Either side can remove a friend at any time (silent — no notification to the other).
- Friends see: display name, avatar, weekly XP (ceilinged), challenge results, badge
  count. Nothing else — no streaks-shaming, no "last active", no progress details.

## Challenges — async, bounded, preset-only communication

| Type | What's compared | Window |
|------|-----------------|--------|
| **Daily duel** | today's shared daily-challenge score (`10`) — both play the same puzzle | that day |
| **Set duel** | total time + accuracy on the same fixed character set (e.g. 5 chars from the challenger's mastered pool that both have unlocked) | 72 h |
| **Weekly board** | friends-only XP this week, per-session ceiling applied | Mon–Sun |

- Lifecycle: challenge issued → friend accepts (or it expires silently) → both play →
  results visible to both. Declining/expiring is consequence-free and unannounced.
- **Reactions:** a small preset set (👏 🔥 😮 🤝) — the only inter-user signal besides
  scores. No free text, ever.
- **Integrity** (adapted from PR #1 — low-stakes, bounds-only): submissions carry a
  compact play log (per-char verdicts + timestamps); a Cloud Function checks duration
  plausibility, theoretical-max score, monotonic timestamps, and rate limits. Failures
  are dropped silently — this is a friends game, not an esports ladder. XP counts toward
  boards only up to a **per-session ceiling** so grinding can't dominate (`10`).

## Progress sync

- **What syncs:** `CharacterProgress` (SRS state), `ReviewLog` (append-only),
  earned badges, settings that matter across devices (daily cap). Content tables never
  sync — they ship with the app.
- **Model:** local-first with an **outbox** (Room table drained by WorkManager on
  connectivity, client-generated UUIDs for idempotent retries — PR #1's design).
  Firestore's offline cache covers reads.
- **Merge rule** (multi-device): per character, the record with the **latest
  `lastReviewedAt` wins**; `ReviewLog` is a set-union (append-only, UUID-keyed); badges
  are a union (a badge once earned is never un-earned by sync).
- **Restore:** fresh install + sign-in pulls the backup and rebuilds local state; the
  review queue recomputes from `dueAt` as always.

## Firestore data model (server side)

```
users/{uid}                    displayName, avatarId, friendCode, createdAt
users/{uid}/progress/{char}    SRS state mirror (backup)
users/{uid}/reviewLog/{id}     append-only review entries
users/{uid}/badges/{badgeId}   earnedAt
friendEdges/{edgeId}           uidA, uidB, confirmedAt        (mutual or absent)
challenges/{id}                type, fromUid, toUid, setSpec, expiresAt,
                               results{uid: score, log}, validated
weeklyXp/{uid_week}            ceilinged XP aggregate (materialized by function)
```

Security rules: a user writes only their own subtree + challenge results they're a party
to; friend data is readable only across a confirmed edge; everything else denied.
Functions: challenge validation (bounds), weekly XP aggregation, friend-code exchange,
account deletion purge.

## Privacy stance (all-ages product)

- **Data minimization:** uid, generated display name, avatar id, scores, SRS backup.
  No birthdate collection, no analytics, no ad IDs, no third-party trackers (`00`).
- **Mixed-audience caution:** we don't age-gate, so the social design assumes children
  are present — hence generated names, no free text, no stranger contact, no public
  profiles. (Formal COPPA/Families-policy review happens at the Publish milestone.)
- Privacy policy ships with the first public release listing exactly the above.

## Audio-generation service note

The pre-generated audio pipeline (`00` amendment; milestone M2, moved up 2026-07-09) runs at **ingest
time**, not runtime: a cloud TTS API (e.g. Google Cloud TTS) is called once per
curriculum character/word per language, and the clips ship as app/data assets — offline
forever, no runtime service. If runtime TTS for arbitrary text is ever needed (unlikely:
device TTS covers Browse), a **Cloudflare Worker proxy with edge caching** is the
designated shape — never an API key in the app.

## Failure modes

| Scenario | Behavior |
|----------|----------|
| Offline at quest end | Local state updates; outbox queues; nothing visible changes. |
| Signed out | Everything works; social surfaces show a calm sign-in card. |
| Challenge submission fails validation | Dropped silently; local play unaffected. |
| Friend removes you | Their data disappears from your views; no notification either way. |
| Backend fully down | App is identical to the signed-out experience (constitution holds). |

## Open questions

- [ ] Challenge set-spec details (who picks the 5 characters for a set duel — challenger's
      choice from the mutual mastered pool vs. random). Lean: random from the
      intersection, so duels are fair by construction.
- [ ] Weekly XP ceiling value — tune once real session data exists.
- [ ] Whether the son's demo-day friends become the first beta cohort (nice loop with
      `11`'s S5). Product call, not a spec blocker.
- [ ] Leagues (PR #1): revisit only after friends challenges have real usage.
