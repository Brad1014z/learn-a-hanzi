# Pilot measurement worksheet

The M4.1 pilot uses a **facilitator-led worksheet**, not app telemetry. The signed pilot
APK contains no upload path and its stroke exporter remains a no-op; this keeps minors'
data off the network and out of the app. The protocol is in
[`docs/milestones/m4.1-pilot-protocol.md`](../../docs/milestones/m4.1-pilot-protocol.md).

## Files

| File | Purpose |
|---|---|
| `pilot-results-template.csv` | the worksheet: one row per pseudonymous participant |
| `pilot-results.csv` | the real worksheet (git-ignored; keep it local) |
| `pilot-report.md` | generated gate evidence (commit when the pilot is real) |

## Workflow

```bash
cp data/pilot/pilot-results-template.csv data/pilot/pilot-results.csv
# fill one row per participant, then:
./gradlew :data-ingest:analyzePilotResults
./gradlew :data-ingest:checkPilotResults   # exits non-zero unless every gate passes
```

The report is written to `data/pilot/pilot-report.md`.

## Columns

| Column | Meaning |
|---|---|
| `participantId` | pseudonym (`P1`…`P8`); never a name |
| `guardianConsent` | `yes` only after the signed guardian consent is on file |
| `teenAssent` | `yes` only after the teen agreed to take part |
| `firstQuestDate` | ISO date of the first quest |
| `firstQuestUnaided` | `yes` if the learner finished the core quest without facilitator help (in-app hints are allowed) |
| `coreDurationSeconds` | start of the core quest → chest opened |
| `fairness1to5` | answer to “How fair did the feedback feel?” (1–5) |
| `nextDayDate` | ISO date of the recall check; must be the day after the first quest |
| `nextDayRecallCount` | how many of 人/大/天 the learner wrote from memory (0–3) |
| `thirdSessionDate` | ISO date of the third session; must be 1–4 days after the first quest |
| `incidents` | blank, or a short description of a crash, lost progress, duplicate reward, or blocked journey |
| `notes` | anything the analysis should not infer (withdrawal, scheduling, facilitator help) |

An incomplete row is reported under “Not counted” and does not enter the rates. A
withdrawal is not a failure, but it does not count toward the 5–8 complete participants.

## What to commit

- **Do commit** `pilot-report.md` once it is real: it is the gate evidence and contains
  only pseudonymous IDs.
- **Do not commit** `pilot-results.csv` or any signed consent forms. Keep consent forms
  separate from the worksheet and store them per the protocol.
