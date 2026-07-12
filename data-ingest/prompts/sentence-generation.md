# Example-sentence generation prompt (spec 02 — LLM-generated sentences)

**Pinned model:** `claude-fable-5`
**Output file:** `data/pinned/sentences-hsk1.json` (reviewed + checked in; the ingest
tool consumes the file, never an API)
**Regeneration:** deliberate, manual step — run this prompt against the pinned model
(API or interactive session), then human-review before check-in. The ingest tool
hard-validates the vocabulary constraint either way.

---

## Prompt

You are generating example sentences for a children's Chinese-writing app
(all-ages playful; learners are ~6–14 and adult beginners).

For each character in the attached HSK 1 character list, write ONE short Mandarin
example sentence that:

1. **Contains the target character.**
2. **Uses ONLY characters from the attached HSK 1 list** — every single hanzi in the
   sentence must be on the list. Chinese punctuation (。，！？、) is allowed. This is a
   hard constraint; the build fails on violations.
3. Is **4–10 characters** long and natural, everyday Mandarin — something a kid would
   actually say or hear (meals, family, pets, school, weather, time).
4. Is **all-ages friendly**: no romance, violence, alcohol, money worries beyond
   pocket-money scale, or brand names.
5. Prefers **repetition of a small set of common words** across sentences
   (comprehensible input): repeated frames like 我有…/…在…/…很… are a feature.

For each sentence also provide:
- `pinyin`: tone-marked, word-spaced, lowercase, **canonical dictionary tones**
  (no tone-sandhi respelling: write 不 as bù, 一 as yī); neutral tone for particles
  (de, le, ma, ne, men) and reduplicated kin terms (māma, bàba, jiějie).
- `english`: a short natural translation.

Output one JSON object per line:
`{"character": "…", "text": "…", "pinyin": "…", "english": "…", "approved": true}`

## Review rules (human, before check-in)

- Read every line. Edit or delete anything unnatural, wrong, or off-tone.
- `approved: false` keeps a line out of the dataset without deleting it.
- Merging the PR that adds/changes this file **is** the approval record (spec 02).
