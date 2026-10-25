package io.github.brad1014z.hanzi.ingest

import io.github.brad1014z.hanzi.engine.data.LessonContent
import io.github.brad1014z.hanzi.engine.data.LessonContentValidationException
import io.github.brad1014z.hanzi.engine.data.LessonManifest
import io.github.brad1014z.hanzi.engine.data.PrimaryReading
import io.github.brad1014z.hanzi.engine.data.PronunciationAudio
import io.github.brad1014z.hanzi.engine.data.TeacherReview
import io.github.brad1014z.hanzi.engine.data.UsageExample
import io.github.brad1014z.hanzi.engine.data.requireValidLessonManifest
import java.io.File
import java.util.Base64
import kotlin.system.exitProcess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds a teacher-review draft of the M4.1 first-30 manifest from the bundled dataset.
 *
 * Why this exists: the release gate asks a Chinese teacher to sign exactly 30 lessons, but
 * the repo only contained an empty manifest and a three-gloss debug fallback. Authoring
 * from scratch (with the "example may only use characters learned so far" rule) is a large
 * job; this generator removes the mechanical part:
 *
 *   - picks 人 → 大 → 天 then the app's curriculum order,
 *   - fills reading, tone, proposed gloss, audio path, and the app's world/sequence,
 *   - includes a dataset example **only** when it already satisfies the learned-vocabulary
 *     rule; otherwise it omits it and says so,
 *   - validates the draft against every structural rule (with approvals intentionally off),
 *   - writes a self-contained review sheet with embedded audio, plus CSV.
 *
 * It never writes `approved = true`, never edits the app asset, and never infers approval.
 * The teacher edits the draft, then the approved result is copied to
 * `app/src/main/assets/content/lesson-content-first30.json`.
 */
fun main(args: Array<String>) {
    val options = parseDraftOptions(args)
    val root = File(".").canonicalFile
    val databaseFile = resolveFromRoot(root, options.database)
    val assetsDir = resolveFromRoot(root, options.assets)
    val outDir = resolveFromRoot(root, options.outDir).apply { mkdirs() }

    val source = loadManifestSource(databaseFile)
    val draft = buildDraft(source, assetsDir, options.count, characters = options.characters)

    try {
        requireValidLessonManifest(
            manifest = LessonManifest(DRAFT_CONTENT_VERSION, draft.map { it.lesson }),
            dictionaryReadings = source.readings,
            audioExists = { assetPath -> File(assetsDir, assetPath).isFile },
            requiredCount = options.count,
            requireApproval = false,
        )
    } catch (error: LessonContentValidationException) {
        System.err.println(
            error.failures.joinToString(
                prefix = "Draft is not structurally valid:\n- ",
                separator = "\n- ",
            ),
        )
        exitProcess(1)
    }

    val jsonFile = File(outDir, "lesson-content-first30.draft.json")
    val htmlFile = File(outDir, "lesson-content-first30-review.html")
    val csvFile = File(outDir, "lesson-content-first30-review.csv")

    jsonFile.writeText(
        prettyJson.encodeToString(
            LessonManifest(DRAFT_CONTENT_VERSION, draft.map { it.lesson }),
        ) + "\n",
    )
    htmlFile.writeText(renderHtml(draft, source, options.count))
    csvFile.writeText(renderCsv(draft))

    val withExamples = draft.count { it.lesson.usageExample != null }
    val noted = draft.count { it.notes.isNotEmpty() }
    println(
        """
        Draft written to ${outDir.relativeTo(root).path}/
          ${jsonFile.name}   ${draft.size} lessons (approvals intentionally absent)
          ${htmlFile.name}   self-contained review sheet with embedded audio
          ${csvFile.name}    spreadsheet-friendly review sheet
          compliant dataset examples: $withExamples/${draft.size}
          lessons with review notes: $noted/${draft.size}
        """.trimIndent(),
    )
}

const val DRAFT_CONTENT_VERSION = "m4.1-draft-for-teacher-review"

private val prettyJson = Json { prettyPrint = true }

/**
 * Curated starter examples for the first three lessons, where no dataset word is
 * compliant yet. They are suggestions for the teacher, not approved content.
 */
private val STARTER_EXAMPLES = mapOf(
    "人" to UsageExample("人人", "rén rén", "everyone", "rén"),
    "大" to UsageExample("大人", "dà rén", "adult", "dà"),
    "天" to UsageExample("天天", "tiān tiān", "every day", "tiān"),
)

data class DraftLesson(
    val lesson: LessonContent,
    val dictionaryDefinition: String,
    val curriculum: CurriculumInfo?,
    val audioFile: File?,
    val notes: List<String>,
    val allowedCharacters: List<String>,
)

fun buildDraft(
    source: ManifestSource,
    assetsDir: File,
    count: Int = 30,
    requiredPrefix: List<String> = listOf("人", "大", "天"),
    characters: List<String>? = null,
): List<DraftLesson> {
    val selected = characters?.distinct()
        ?: (requiredPrefix + source.curriculumOrder.filterNot { it in requiredPrefix })
            .distinct()
            .take(count)
    require(selected.size == count) {
        "Need exactly $count characters, got ${selected.size}: ${selected.joinToString("")}"
    }
    require(selected.take(requiredPrefix.size) == requiredPrefix) {
        "The first ${requiredPrefix.size} lessons must be ${requiredPrefix.joinToString(" → ")}"
    }
    val unknown = selected.filterNot { it in source.readings }
    require(unknown.isEmpty()) { "Characters not in the bundled dataset: ${unknown.joinToString("")}" }

    val learned = linkedSetOf<String>()
    return selected.mapIndexed { index, character ->
        val readings = source.readings[character]
            ?: error("$character has no dictionary reading in the bundled dataset")
        val primary = readings.firstOrNull()
            ?: error("$character has an empty reading list in the bundled dataset")
        val tone = toneOf(primary)
        val allowed = (learned + character).toList()
        val notes = mutableListOf<String>()

        val datasetSentence = source.sentences[character]
        val datasetExample = datasetSentence?.let { sentence ->
            val targetInText = character in sentence.text
            val targetInPinyin = primary.lowercase() in pinyinSyllables(sentence.pinyin)
            val future = sentence.text
                .filter(::isHanzi)
                .map(Char::toString)
                .filterNot { it in allowed }
                .distinct()
            when {
                !targetInText -> {
                    notes += "Dataset sentence omitted: it does not contain the target."
                    null
                }
                !targetInPinyin -> {
                    notes += "Dataset sentence omitted: its pinyin does not contain '$primary' as a syllable."
                    null
                }
                future.isNotEmpty() -> {
                    notes += "Dataset sentence omitted: it uses future characters ${future.joinToString(" ")}."
                    null
                }
                else -> UsageExample(
                    text = sentence.text,
                    segmentedPinyin = sentence.pinyin,
                    translation = sentence.english,
                    targetReading = primary,
                )
            }
        }

        // A dataset word is a useful suggestion when it is composed only of characters
        // learned by this lesson. It is not a sentence; the review sheet says so.
        val suggestedWord = source.words.firstOrNull { word ->
            character in word.simplified &&
                word.simplified.length in 2..4 &&
                word.simplified.all(::isHanzi) &&
                word.simplified.all { it.toString() in allowed } &&
                primary.lowercase() in pinyinSyllables(word.pinyin) &&
                word.english.isNotBlank()
        }
        val wordExample = suggestedWord?.let {
            UsageExample(
                text = it.simplified,
                segmentedPinyin = it.pinyin,
                translation = it.english,
                targetReading = primary,
            )
        }
        val starterExample = STARTER_EXAMPLES[character]
        val example = datasetExample ?: wordExample ?: starterExample

        if (datasetExample == null) {
            if (suggestedWord != null) {
                notes += "Suggested word from the dataset: ${suggestedWord.simplified} " +
                    "(${suggestedWord.pinyin}) — confirm or expand into a sentence."
            } else if (starterExample != null) {
                notes += "Suggested starter example (not from the dataset) — confirm."
            } else {
                notes += "No compliant example; write one or leave the example empty."
            }
        }

        val audioPath = audioAssetPath(character)
        val audioFile = File(assetsDir, audioPath).takeIf { it.isFile }
        if (audioFile == null) notes += "Missing audio clip $audioPath."

        val gloss = learnerGloss(source.definitions[character].orEmpty())
        notes += "Proposed gloss is the first dictionary sense — confirm it is the sense to teach first."
        val lesson = LessonContent(
            character = character,
            primaryReading = PrimaryReading(primary, tone),
            learnerGloss = gloss,
            usageExample = example,
            pronunciationAudio = PronunciationAudio(character, audioPath),
            secondaryReadings = readings.drop(1),
            curriculumSequence = index + 1,
            contentVersion = DRAFT_CONTENT_VERSION,
            review = TeacherReview(approved = false, reviewer = "", reviewedAt = null),
        )
        learned += character
        DraftLesson(
            lesson = lesson,
            dictionaryDefinition = source.definitions[character].orEmpty(),
            curriculum = source.worldByCharacter[character],
            audioFile = audioFile,
            notes = notes,
            allowedCharacters = allowed,
        )
    }
}

private fun learnerGloss(definition: String): String =
    definition.substringBefore(";").substringBefore(",").trim().ifBlank { definition.trim() }

private fun toneOf(pinyin: String): Int {
    val marks = listOf("āēīōūǖ", "áéíóúǘ", "ǎěǐǒǔǚ", "àèìòùǜ")
    val found = marks.indexOfFirst { set -> pinyin.any { it in set } }
    return if (found < 0) 5 else found + 1
}

private fun pinyinSyllables(text: String): List<String> = text.lowercase()
    .split(Regex("[^a-züāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+"))
    .filter(String::isNotBlank)

// ---------------------------------------------------------------------------
// Review sheet
// ---------------------------------------------------------------------------

private fun renderHtml(draft: List<DraftLesson>, source: ManifestSource, count: Int): String {
    val noted = draft.count { it.notes.isNotEmpty() }
    val examples = draft.count { it.lesson.usageExample != null }
    val body = buildString {
        for (entry in draft) {
            val lesson = entry.lesson
            val world = entry.curriculum
            append("<tr>")
            append("<td class=num>${lesson.curriculumSequence}</td>")
            append("<td class=hanzi>${html(lesson.character)}</td>")
            append("<td><b>${html(lesson.primaryReading.pinyin)}</b><br><span class=muted>tone ${lesson.primaryReading.tone}</span></td>")
            append("<td><b>${html(lesson.learnerGloss)}</b><br><span class=muted>${html(entry.dictionaryDefinition)}</span></td>")
            append("<td>")
            val example = lesson.usageExample
            if (example != null) {
                append("<div class=example>${html(example.text)}</div>")
                append("<div class=muted>${html(example.segmentedPinyin)}</div>")
                append("<div class=muted>${html(example.translation)}</div>")
            } else {
                append("<span class=missing>Needs a compliant example (or leave empty)</span>")
            }
            append("</td>")
            append("<td>")
            entry.audioFile?.let { file ->
                val base64 = Base64.getEncoder().encodeToString(file.readBytes())
                append("<audio controls preload=none src=\"data:audio/mpeg;base64,$base64\"></audio>")
            } ?: append("<span class=missing>missing</span>")
            append("<br><span class=muted>${html(lesson.pronunciationAudio.assetPath)}</span>")
            append("</td>")
            append("<td>")
            if (world != null) {
                append("${html(world.worldName)}<br><span class=muted>app #${world.sequence}</span>")
            } else {
                append("<span class=muted>not in curriculum</span>")
            }
            append("</td>")
            append("<td>")
            append("<div class=muted>allowed so far: ${html(entry.allowedCharacters.joinToString(""))}</div>")
            if (entry.notes.isEmpty()) {
                append("<span class=ok>no flags</span>")
            } else {
                append("<ul>")
                for (note in entry.notes) append("<li>${html(note)}</li>")
                append("</ul>")
            }
            append("</td>")
            append("</tr>")
        }
    }

    return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>M4.1 first-30 lesson review — DRAFT</title>
<style>
  body { font: 15px/1.45 system-ui, sans-serif; margin: 2rem auto; max-width: 1400px; padding: 0 1rem; color: #1b1b1b; }
  h1 { margin-bottom: .2rem; }
  .banner { background: #fff4d6; border: 1px solid #e2b84b; padding: .8rem 1rem; border-radius: 6px; }
  table { border-collapse: collapse; width: 100%; margin-top: 1.2rem; }
  th, td { border-bottom: 1px solid #ddd; padding: .55rem .5rem; vertical-align: top; text-align: left; }
  th { position: sticky; top: 0; background: #f6f6f6; }
  .num { color: #777; width: 2.5rem; }
  .hanzi { font-size: 2.1rem; line-height: 1; width: 4rem; }
  .muted { color: #666; font-size: .88rem; }
  .missing { color: #a33; }
  .ok { color: #2c6b3f; }
  .example { font-size: 1.15rem; }
  ul { margin: 0; padding-left: 1.1rem; }
  audio { width: 190px; height: 32px; }
  .summary { display: flex; gap: 2rem; flex-wrap: wrap; margin: 1rem 0; }
  .summary b { font-size: 1.4rem; display: block; }
  @media print { body { max-width: none; } th { position: static; } audio { display: none; } }
</style>
</head>
<body>
<h1>M4.1 first-30 lesson review — DRAFT</h1>
<p><b>Status: draft for Chinese-teacher review. Not approved. Do not distribute.</b></p>
<div class=banner>
  <b>How to review</b>
  <ol>
    <li>Check each character's reading, tone, learner gloss, and example (character, pinyin, translation).</li>
    <li>Pinyin must be <b>syllable-segmented</b> for the target check: write <code>jīn tiān</code>, not <code>jīntiān</code>.</li>
    <li>Where an example is missing or flagged, write a short sentence that uses only the characters listed under “allowed so far”, or leave the example empty.</li>
    <li>Edit <code>lesson-content-first30.draft.json</code> with corrections. Keep <code>review.approved</code> false until you have reviewed the whole set.</li>
    <li>When every entry is correct, set <code>review.approved</code> to true, fill <code>review.reviewer</code> and <code>review.reviewedAt</code>, then copy the file to <code>app/src/main/assets/content/lesson-content-first30.json</code>.</li>
  </ol>
  Audio is embedded, so this page works offline. The CSV is the same content for spreadsheet comments.
</div>
<div class=summary>
  <div><b>$count</b> lessons</div>
  <div><b>$examples</b> compliant dataset examples</div>
  <div><b>$noted</b> lessons with review notes</div>
</div>
<table>
<thead><tr><th>#</th><th>Char</th><th>Reading</th><th>Gloss / dictionary</th><th>Example</th><th>Audio</th><th>World</th><th>Notes</th></tr></thead>
<tbody>
$body
</tbody>
</table>
<p class=muted>Generated from ${html("app/src/main/assets/databases/hanzi_v1.sqlite")} (${source.curriculum.size} curriculum characters). The draft never writes approval metadata.</p>
</body>
</html>
"""
}

private fun renderCsv(draft: List<DraftLesson>): String {
    val header = listOf(
        "sequence", "character", "pinyin", "tone", "proposedGloss", "dictionaryDefinition",
        "exampleText", "examplePinyin", "exampleTranslation", "audioPath",
        "world", "worldSequence", "allowedSoFar", "notes",
    )
    val rows = draft.map { entry ->
        val lesson = entry.lesson
        val example = lesson.usageExample
        listOf(
            lesson.curriculumSequence.toString(),
            lesson.character,
            lesson.primaryReading.pinyin,
            lesson.primaryReading.tone.toString(),
            lesson.learnerGloss,
            entry.dictionaryDefinition,
            example?.text.orEmpty(),
            example?.segmentedPinyin.orEmpty(),
            example?.translation.orEmpty(),
            lesson.pronunciationAudio.assetPath,
            entry.curriculum?.worldName.orEmpty(),
            entry.curriculum?.sequence?.toString().orEmpty(),
            entry.allowedCharacters.joinToString(""),
            entry.notes.joinToString(" | "),
        )
    }
    return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csv(it) } } + "\n"
}

private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

private fun html(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

private data class DraftOptions(
    val database: String = "app/src/main/assets/databases/hanzi_v1.sqlite",
    val assets: String = "app/src/main/assets",
    val outDir: String = "data/review/lesson-content-first30",
    val count: Int = 30,
    val characters: List<String>? = null,
)

private fun parseDraftOptions(args: Array<String>): DraftOptions {
    var options = DraftOptions()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--database" -> options = options.copy(database = args[++i])
            "--assets" -> options = options.copy(assets = args[++i])
            "--out-dir" -> options = options.copy(outDir = args[++i])
            "--count" -> options = options.copy(count = args[++i].toInt())
            "--characters" -> options = options.copy(
                characters = args[++i].split(',', ' ')
                    .map(String::trim)
                    .filter(String::isNotEmpty),
            )
            else -> error("Unknown argument: $arg")
        }
        i++
    }
    return options
}
