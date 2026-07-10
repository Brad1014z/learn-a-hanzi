package io.github.brad1014z.hanzi.ingest

import io.github.brad1014z.hanzi.engine.data.HanziCoordinates
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.svg.SvgPathParser
import io.github.brad1014z.hanzi.engine.svg.mapPoints
import io.github.brad1014z.hanzi.engine.svg.toPathString
import java.io.File
import java.sql.Connection
import kotlin.math.round

/**
 * The ingestion pipeline (spec 02, milestone M2): data/raw + data/pinned →
 * app/src/main/assets/databases/hanzi_v1.sqlite + data/ingest-report.md.
 * Deterministic: same inputs → same bytes; the network-touching steps live in
 * Download.kt (raw sources) and the reviewed pinned files (LLM sentences).
 */

const val DATASET_VERSION =
    "mmah-bddc96d+cedict-20260709+unihan-16.0.0+tatoeba-20260709+hsk1-clem109+sent-r1"

private data class CharRow(
    val character: String,
    val pinyin: List<String>,
    val definition: String,
    val radical: String?,
    val strokeCount: Int,
    val freqRank: Int?,
    val decomposition: String?,
    val etymologyHint: String?,
    val strokes: List<String>, // normalized SVG outline paths
    val medians: List<String>, // normalized medians as JSON
)

private data class WordRow(
    val id: Long,
    val simplified: String,
    val traditional: String?,
    val pinyin: String,
    val english: String,
    val freqRank: Int,
)

fun main() {
    val root = File(".").canonicalFile
    val raw = File(root, "data/raw")
    val pinned = File(root, "data/pinned")
    val report = StringBuilder()
    val warnings = mutableListOf<String>()

    verifyLock(root)

    // -- Load the curriculum + extra teachable characters ---------------------
    val hskChars = hskCharacters(File(pinned, "hsk1-words.json"))
    val prototypeChars = json.decodeFromString<List<String>>(
        object {}.javaClass.getResourceAsStream("/characters/index.json")!!
            .bufferedReader().readText(),
    )
    val extraChars = (prototypeChars - hskChars.toSet()).sorted()
    val allChars = (hskChars + extraChars).toSortedSet()
    println("HSK 1: ${hskChars.size} chars; extra (Phase 0 prototype): ${extraChars.size}")

    // -- Parse + validate sources (⚠ verify flags close here) -----------------
    val graphics = parseGraphics(File(raw, "graphics.txt"), allChars)
    val dict = parseDictionary(File(raw, "dictionary.txt"), allChars)
    val missing = hskChars.filter { graphics[it] == null || dict[it]?.definition.isNullOrBlank() }
    check(missing.isEmpty()) {
        "HARD FAIL (spec 02/04): HSK 1 characters lack complete data: $missing"
    }
    val droppedExtras = extraChars.filter { graphics[it] == null || dict[it] == null }
    if (droppedExtras.isNotEmpty()) warnings += "non-curriculum chars dropped (incomplete): $droppedExtras"

    val unihanStrokes = parseUnihanStrokeCounts(File(raw, "Unihan_IRGSources.txt"), allChars)
    for (ch in allChars) {
        val ours = graphics[ch]?.strokes?.size ?: continue
        val unihan = unihanStrokes[ch]
        if (unihan != null && unihan != ours) {
            warnings += "stroke-count cross-check: $ch has $ours strokes in mmah, $unihan in Unihan"
        }
    }

    // -- Frequency (Tatoeba cmn corpus — spec 02) ------------------------------
    println("counting Tatoeba corpus…")
    val corpus = countCorpus(File(raw, "cmn_sentences_detailed.tsv"), allChars)
    val cedict = parseCedict(File(raw, "cedict.txt"))
    val cedictMembership: Map<String, Int> = allChars.associateWith { ch ->
        cedict.count { ch in it.simplified }
    }
    // Rank over every character seen in the corpus (deterministic tie-breaks).
    val rankedChars = corpus.charCounts.keys.sortedWith(
        compareByDescending<String> { corpus.charCounts[it] ?: 0L }
            .thenByDescending { cedictMembership[it] ?: 0 }
            .thenBy { it },
    )
    val freqRank: Map<String, Int> = rankedChars.withIndex().associate { (i, ch) -> ch to i + 1 }

    // -- Character rows (normalized geometry via the engine — spec 02 step 3) --
    val charRows = allChars.map { ch ->
        val g = graphics.getValue(ch)
        val d = dict.getValue(ch)
        CharRow(
            character = ch,
            pinyin = d.pinyin,
            definition = d.definition,
            radical = d.radical?.takeIf { it.isNotBlank() },
            strokeCount = g.strokes.size,
            freqRank = freqRank[ch],
            decomposition = d.decomposition?.takeIf { it.isNotBlank() && !it.startsWith("？") },
            etymologyHint = d.etymology?.hint?.takeIf { it.isNotBlank() },
            strokes = g.strokes.map { path ->
                SvgPathParser.parse(path).mapPoints(HanziCoordinates::normalize).toPathString()
            },
            medians = g.medians.map { median ->
                median.joinToString(",", "[", "]") { (x, y) ->
                    val p = HanziCoordinates.normalize(Point(x, y))
                    "[${round(p.x * 10) / 10},${round(p.y * 10) / 10}]"
                }
            },
        )
    }

    // -- Worlds + world-major teaching sequence (spec 04) ----------------------
    val worlds = parseWorlds(File(pinned, "worlds-hsk1.tsv"))
    val assigned = worlds.flatMap { it.characters }
    val dupes = assigned.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val unassigned = hskChars.toSet() - assigned.toSet()
    val offList = assigned.toSet() - hskChars.toSet()
    check(dupes.isEmpty() && unassigned.isEmpty() && offList.isEmpty()) {
        "HARD FAIL: worlds file must partition HSK 1 exactly — dupes=$dupes unassigned=$unassigned offList=$offList"
    }
    val worldOrder = worlds.sortedByDescending { w ->
        w.characters.sumOf { corpus.charCounts[it] ?: 0L }
    }
    data class CurriculumRow(val character: String, val sequence: Int, val world: String, val worldName: String)
    var seq = 0
    val curriculum = worldOrder.flatMap { world ->
        world.characters.sortedWith(
            compareBy<String> { freqRank[it] ?: Int.MAX_VALUE }
                .thenBy { graphics.getValue(it).strokes.size }
                .thenBy { dict.getValue(it).radical ?: "" }
                .thenBy { it },
        ).map { ch -> CurriculumRow(ch, ++seq, world.id, world.name) }
    }

    // -- Word selection (CEDICT, ranked by corpus n-gram counts — spec 02) -----
    val hskSet = hskChars.toSet()
    val badGloss = Regex(
        "^(surname |variant of|old variant|see |abbr\\. )|\\b(sex|porn|prostitut|damn|fuck)",
        RegexOption.IGNORE_CASE,
    )
    val candidates = cedict.asSequence()
        .filter { it.simplified.length in 2..4 && it.simplified.all(::isHanzi) }
        .filter { e -> e.simplified.any { it.toString() in allChars } }
        .filter { it.pinyinNumbered.first().isLowerCase() } // uppercase pinyin = proper noun
        .filter { !badGloss.containsMatchIn(it.glosses.first()) }
        .distinctBy { it.simplified } // first entry wins (CEDICT lists most-common first)
        .toList()
    val wordScore = { e: CedictEntry -> corpus.ngramCounts[e.simplified] ?: 0L }
    val selectedByChar: Map<String, List<CedictEntry>> = allChars.associateWith { ch ->
        candidates.asSequence()
            .filter { ch in it.simplified }
            .sortedWith(
                compareByDescending<CedictEntry> { e -> e.simplified.all { it.toString() in hskSet } }
                    .thenByDescending(wordScore)
                    .thenBy { it.simplified.length }
                    .thenBy { it.simplified },
            )
            .take(3)
            .toList()
    }
    val charsWithoutWords = hskChars.filter { selectedByChar[it].isNullOrEmpty() }
    if (charsWithoutWords.isNotEmpty()) warnings += "HSK chars with no example word: $charsWithoutWords"
    val allSelected = selectedByChar.values.flatten().distinctBy { it.simplified }
        .sortedWith(compareByDescending(wordScore).thenBy { it.simplified })
    val wordRows = allSelected.mapIndexed { i, e ->
        WordRow(
            id = (i + 1).toLong(),
            simplified = e.simplified,
            traditional = e.traditional.takeIf { it != e.simplified },
            pinyin = e.pinyinMarked,
            english = e.glosses.take(3).joinToString("; "),
            freqRank = i + 1,
        )
    }
    val wordIdBySimplified = wordRows.associate { it.simplified to it.id }

    // -- LLM sentences (reviewed, checked in — spec 02) -------------------------
    val sentenceFile = parseSentences(File(pinned, "sentences-hsk1.json"))
    val approved = sentenceFile.sentences.filter { it.approved }
    for (s in approved) {
        check(s.character in hskSet) { "sentence for off-curriculum char ${s.character}" }
        check(s.character in s.text) { "sentence for ${s.character} does not contain it: ${s.text}" }
        val offListChars = s.text.filter { isHanzi(it) && it.toString() !in hskSet }
        check(offListChars.isEmpty()) {
            "HARD FAIL: sentence for ${s.character} uses off-list hanzi '$offListChars': ${s.text}"
        }
        // Soft cross-check: the sentence-level pinyin should contain a reading of the char.
        val readings = dict.getValue(s.character).pinyin.map { it.lowercase() }
        if (readings.isNotEmpty() && readings.none { stripTones(it) in stripTones(s.pinyin.lowercase()) }) {
            warnings += "pinyin cross-check: sentence for ${s.character} (${s.pinyin}) has none of $readings"
        }
    }
    val noSentence = hskChars.filter { ch -> approved.none { it.character == ch } }
    if (noSentence.isNotEmpty()) warnings += "HSK chars with no approved sentence (word fallback applies): $noSentence"
    val sentencesSorted = approved.sortedBy { it.character }

    // -- Write the asset ---------------------------------------------------------
    val schemaJson = File(root, "app/schemas/io.github.brad1014z.hanzi.data.HanziDatabase/2.json")
    val target = File(root, "app/src/main/assets/databases/hanzi_v1.sqlite")
    SqliteWriter(schemaJson, target).write { conn ->
        conn.insert("Character (character, lang, pinyin, definition, radical, strokeCount, freqRank, decomposition, etymologyHint)", charRows) { st, r ->
            st.setString(1, r.character); st.setString(2, "zh-Hans")
            st.setString(3, r.pinyin.joinToString("\",\"", "[\"", "\"]"))
            st.setString(4, r.definition); st.setStringOrNull(5, r.radical)
            st.setInt(6, r.strokeCount); st.setIntOrNull(7, r.freqRank)
            st.setStringOrNull(8, r.decomposition); st.setStringOrNull(9, r.etymologyHint)
        }
        conn.insert("StrokePath (character, strokeIndex, pathData, median)", charRows.flatMap { r ->
            r.strokes.indices.map { i -> Triple(r.character, i, r.strokes[i] to r.medians[i]) }
        }) { st, (ch, i, pm) ->
            st.setString(1, ch); st.setInt(2, i); st.setString(3, pm.first); st.setString(4, pm.second)
        }
        conn.insert("CurriculumEntry (curriculumId, character, level, sequence, world, worldName)", curriculum) { st, r ->
            st.setString(1, "hsk"); st.setString(2, r.character); st.setInt(3, 1)
            st.setInt(4, r.sequence); st.setString(5, r.world); st.setString(6, r.worldName)
        }
        conn.insert("Word (id, lang, simplified, traditional, pinyin, english, freqRank)", wordRows) { st, r ->
            st.setLong(1, r.id); st.setString(2, "zh-Hans"); st.setString(3, r.simplified)
            st.setStringOrNull(4, r.traditional); st.setString(5, r.pinyin)
            st.setString(6, r.english); st.setInt(7, r.freqRank)
        }
        conn.insert("WordCharacter (wordId, character, position)", wordRows.flatMap { w ->
            w.simplified.toList().mapIndexedNotNull { pos, ch ->
                if (ch.toString() in allChars) Triple(w.id, ch.toString(), pos) else null
            }.distinctBy { it.second } // one row per (word, char)
        }) { st, (id, ch, pos) -> st.setLong(1, id); st.setString(2, ch); st.setInt(3, pos) }
        conn.insert("Sentence (id, lang, text, pinyin, english, source, contributor, forCharacter)", sentencesSorted.mapIndexed { i, s -> (i + 1).toLong() to s }) { st, (id, s) ->
            st.setLong(1, id); st.setString(2, "zh-Hans"); st.setString(3, s.text)
            st.setString(4, s.pinyin); st.setString(5, s.english)
            st.setString(6, "llm"); st.setString(7, sentenceFile.meta.model)
            st.setString(8, s.character)
        }
        conn.insert("SentenceCharacter (sentenceId, character, position)", sentencesSorted.mapIndexed { i, s -> (i + 1).toLong() to s }.flatMap { (id, s) ->
            s.text.toList().mapIndexedNotNull { pos, ch ->
                if (ch.toString() in hskSet) Triple(id, ch.toString(), pos) else null
            }.distinctBy { it.second }
        }) { st, (id, ch, pos) -> st.setLong(1, id); st.setString(2, ch); st.setInt(3, pos) }
        conn.insert("Meta (`key`, value)", listOf(
            "datasetVersion" to DATASET_VERSION,
            "schemaVersion" to "2",
        )) { st, (k, v) -> st.setString(1, k); st.setString(2, v) }
    }

    // -- Report + attribution manifest (spec 02 step 10) -------------------------
    report.appendLine("# Ingest report — dataset `$DATASET_VERSION`")
    report.appendLine()
    report.appendLine("Generated by `./gradlew :data-ingest:run` (deterministic; do not edit).")
    report.appendLine()
    report.appendLine("| What | Count |")
    report.appendLine("|---|---|")
    report.appendLine("| HSK 1 curriculum characters | ${hskChars.size} |")
    report.appendLine("| Extra teachable characters (Phase 0 prototype) | ${extraChars.size} |")
    report.appendLine("| Stroke rows | ${charRows.sumOf { it.strokes.size }} |")
    report.appendLine("| Words | ${wordRows.size} |")
    report.appendLine("| LLM sentences (approved) | ${sentencesSorted.size} |")
    report.appendLine("| Tatoeba cmn sentences counted for frequency | (corpus only — no sentences shipped) |")
    report.appendLine()
    report.appendLine("## World order (by aggregate corpus frequency — spec 04)")
    report.appendLine()
    worldOrder.forEachIndexed { i, w ->
        report.appendLine("${i + 1}. **${w.name}** (`${w.id}`, ${w.characters.size} chars)")
    }
    report.appendLine()
    report.appendLine("## Top 10 most frequent curriculum characters")
    report.appendLine()
    hskChars.sortedBy { freqRank[it] ?: Int.MAX_VALUE }.take(10).forEach {
        report.appendLine("- $it (corpus rank ${freqRank[it]})")
    }
    report.appendLine()
    report.appendLine("## Warnings (${warnings.size})")
    report.appendLine()
    warnings.forEach { report.appendLine("- $it") }
    report.appendLine()
    report.appendLine("## Attribution manifest")
    report.appendLine()
    report.appendLine("| Source | License | Used for |")
    report.appendLine("|---|---|---|")
    report.appendLine("| make-me-a-hanzi `graphics.txt` (commit bddc96d) | Arphic Public License | stroke outlines + medians |")
    report.appendLine("| make-me-a-hanzi `dictionary.txt` (commit bddc96d) | LGPL v3+ | definitions, pinyin, radicals, decomposition |")
    report.appendLine("| CC-CEDICT (snapshot 2026-07-09) | CC BY-SA 4.0 | example words |")
    report.appendLine("| Unihan 16.0.0 | Unicode License | stroke-count cross-checks |")
    report.appendLine("| Tatoeba cmn corpus (snapshot 2026-07-09) | CC-BY 2.0 FR | character/word frequency only — no sentence text shipped |")
    report.appendLine("| LLM sentences | generated by `${sentenceFile.meta.model}`, human-reviewed (PR record) | example sentences (`source=\"llm\"`) |")
    report.appendLine()
    report.appendLine("Full license texts ship with the app credits before Publish (spec 02 checklist).")
    File(root, "data/ingest-report.md").writeText(report.toString())

    println("dataset → ${target.relativeTo(root)} (${"%,d".format(target.length())} bytes)")
    println("report  → data/ingest-report.md (${warnings.size} warnings)")
}

fun stripTones(s: String): String {
    val map = "āáǎà" to 'a'; val e = "ēéěè" to 'e'; val i = "īíǐì" to 'i'
    val o = "ōóǒò" to 'o'; val u = "ūúǔù" to 'u'; val v = "ǖǘǚǜ" to 'ü'
    var out = s
    for ((marks, plain) in listOf(map, e, i, o, u, v)) {
        for (m in marks) out = out.replace(m, plain)
    }
    return out
}

// -- tiny JDBC helpers ---------------------------------------------------------

private fun <T> Connection.insert(tableAndCols: String, rows: List<T>, bind: (java.sql.PreparedStatement, T) -> Unit) {
    val cols = tableAndCols.substringAfter("(").substringBefore(")").split(",").size
    val placeholders = (1..cols).joinToString(",") { "?" }
    prepareStatement("INSERT INTO $tableAndCols VALUES ($placeholders)").use { st ->
        for (row in rows) {
            bind(st, row)
            st.addBatch()
        }
        st.executeBatch()
    }
}

private fun java.sql.PreparedStatement.setStringOrNull(i: Int, v: String?) =
    if (v == null) setNull(i, java.sql.Types.VARCHAR) else setString(i, v)

private fun java.sql.PreparedStatement.setIntOrNull(i: Int, v: Int?) =
    if (v == null) setNull(i, java.sql.Types.INTEGER) else setInt(i, v)
