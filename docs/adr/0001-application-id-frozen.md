---
status: accepted
date: 2026-09-04
---

# The applicationId is `io.github.brad1014z.hanzi`, frozen before the first pilot install

Spec 01 deferred the final `applicationId` to "before the first Play upload". The M4.1
pilot (a signed APK handed out via GitHub Releases) reaches real phones well before any
Play upload, and Android treats a changed `applicationId` as a *different app*: every
pilot tester would have to uninstall and lose their local progress — which would also
destroy the next-day-recall and third-return-session measurements the pilot gates in
`docs/milestones/m4.1-release-gates.md` depend on. So we freeze the existing
`io.github.brad1014z.hanzi` now. It is a namespace the maintainer verifiably owns (their
GitHub user), it is Play-acceptable, and it is independent of the display name — which
lives in `@string/app_name` (currently **Inkbook**), so the co-designer keeps naming
authority (spec 11) without ever touching the id.

## Consequences

- Release signing keys are bound to this id from the first pilot build onward. The
  keystore is never committed (CI decodes it from `KEYSTORE_BASE64`; see
  `.github/workflows/android.yml`) and must be kept safe: both Play and sideloaded
  upgrades reject an APK signed by a different key, so losing it means every tester has
  to uninstall and start over.
- Renaming the app is a one-line `strings.xml` change plus icon work — no id change, no
  reinstall, no lost progress.
