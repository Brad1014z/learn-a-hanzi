package io.github.brad1014z.hanzi.ingest

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true }

fun isHanzi(ch: Char): Boolean = ch in '一'..'鿿'

// ---------------------------------------------------------------------------
// make-me-a-hanzi
// ---------------------------------------------------------------------------

@Serializable
data class GraphicsEntry(
    val character: String,
    val strokes: List<String>,
    val medians: List<List<List<Double>>>,
)

@Serializable
data class EtymologyEntry(val type: String = "", val hint: String? = null)

@Serializable
data class DictEntry(
    val character: String,
    val pinyin: List<String> = emptyList(),
    val definition: String = "",
    val radical: String? = null,
    val decomposition: String? = null,
    val etymology: EtymologyEntry? = null,
)

/** One JSON object per line; parse only the characters we need (spec 02: validate, fail loudly). */
fun parseGraphics(file: File, wanted: Set<String>): Map<String, GraphicsEntry> {
    val out = HashMap<String, GraphicsEntry>(wanted.size * 2)
    file.forEachLine { line ->
        if (line.isBlank()) return@forEachLine
        // Cheap pre-filter: the character appears in the first ~30 chars of the line.
        val head = line.take(40)
        if (wanted.none { it in head }) return@forEachLine
        val entry = json.decodeFromString<GraphicsEntry>(line)
        if (entry.character in wanted) {
            require(entry.strokes.size == entry.medians.size) {
                "${entry.character}: strokes (${entry.strokes.size}) / medians (${entry.medians.size}) not parallel — schema drift"
            }
            require(entry.medians.all { it.size >= 2 }) { "${entry.character}: median with <2 points" }
            out[entry.character] = entry
        }
    }
    return out
}

fun parseDictionary(file: File, wanted: Set<String>): Map<String, DictEntry> {
    val out = HashMap<String, DictEntry>(wanted.size * 2)
    file.forEachLine { line ->
        if (line.isBlank()) return@forEachLine
        val head = line.take(40)
        if (wanted.none { it in head }) return@forEachLine
        val entry = json.decodeFromString<DictEntry>(line)
        if (entry.character in wanted) out[entry.character] = entry
    }
    return out
}

// ---------------------------------------------------------------------------
// CC-CEDICT
// ---------------------------------------------------------------------------

data class CedictEntry(
    val traditional: String,
    val simplified: String,
    val pinyinNumbered: String,
    val glosses: List<String>,
) {
    val pinyinMarked: String by lazy {
        pinyinNumbered.split(" ").joinToString(" ") { numberedSyllableToMarks(it) }
    }
}

private val CEDICT_LINE = Regex("""^(\S+) (\S+) \[([^]]+)] /(.+)/$""")

fun parseCedict(file: File): List<CedictEntry> =
    file.readLines().mapNotNull { line ->
        if (line.startsWith("#")) return@mapNotNull null
        val m = CEDICT_LINE.find(line) ?: return@mapNotNull null
        val (trad, simp, pinyin, glosses) = m.destructured
        CedictEntry(trad, simp, pinyin, glosses.split("/").filter { it.isNotBlank() })
    }

/**
 * CEDICT tone numbers → tone marks ("ni3" → "nǐ"), per the spec 03 decision to store
 * tone-marked pinyin. Mark placement: a/e take it; "ou" marks o; else the last vowel.
 * Tone 5 (neutral) gets no mark. "u:"/"v" mean ü. Erhua "r5" stays "r".
 */
fun numberedSyllableToMarks(syllable: String): String {
    val marks = mapOf(
        'a' to "āáǎà", 'e' to "ēéěè", 'i' to "īíǐì",
        'o' to "ōóǒò", 'u' to "ūúǔù", 'ü' to "ǖǘǚǜ",
    )
    val tone = syllable.lastOrNull()?.digitToIntOrNull() ?: return syllable
    var body = syllable.dropLast(1).replace("u:", "ü").replace("v", "ü")
    if (tone !in 1..5) return syllable
    if (tone == 5) return body
    val lower = body.lowercase()
    val idx = when {
        'a' in lower -> lower.indexOf('a')
        'e' in lower -> lower.indexOf('e')
        "ou" in lower -> lower.indexOf('o')
        else -> lower.indexOfLast { it in "iouü" }
    }
    if (idx < 0) return body // no vowel (e.g. "m", "hng") — leave unmarked
    val vowel = lower[idx]
    val marked = marks[vowel]?.get(tone - 1) ?: return body
    val replacement = if (body[idx].isUpperCase()) marked.uppercaseChar() else marked
    return body.substring(0, idx) + replacement + body.substring(idx + 1)
}

// ---------------------------------------------------------------------------
// Unihan (cross-checks only — spec 02)
// ---------------------------------------------------------------------------

fun parseUnihanStrokeCounts(file: File, wanted: Set<String>): Map<String, Int> {
    val wantedCodepoints = wanted.associateBy { "U+%04X".format(it.codePointAt(0)) }
    val out = HashMap<String, Int>()
    file.forEachLine { line ->
        if (!line.contains("\tkTotalStrokes\t")) return@forEachLine
        val parts = line.split("\t")
        val char = wantedCodepoints[parts[0]] ?: return@forEachLine
        out[char] = parts[2].trim().split(" ").first().toInt()
    }
    return out
}

// ---------------------------------------------------------------------------
// Tatoeba (frequency corpus — spec 02, amended 2026-07-09)
// ---------------------------------------------------------------------------

/** Character counts + substring counts (lengths 2..4) over the full cmn corpus. */
class CorpusCounts(val charCounts: Map<String, Long>, val ngramCounts: Map<String, Long>)

fun countCorpus(file: File, charsOfInterest: Set<String>): CorpusCounts {
    val charCounts = HashMap<String, Long>()
    val ngrams = HashMap<String, Long>()
    file.forEachLine { line ->
        val text = line.split("\t").getOrNull(2) ?: return@forEachLine
        for (ch in text) {
            if (isHanzi(ch)) charCounts.merge(ch.toString(), 1L, Long::plus)
        }
        // n-grams for word-frequency ranking; only those touching a character we teach
        for (n in 2..4) {
            for (i in 0..text.length - n) {
                val gram = text.substring(i, i + n)
                if (gram.any { !isHanzi(it) }) continue
                if (gram.none { it.toString() in charsOfInterest }) continue
                ngrams.merge(gram, 1L, Long::plus)
            }
        }
    }
    return CorpusCounts(charCounts, ngrams)
}

// ---------------------------------------------------------------------------
// Pinned inputs (data/pinned/)
// ---------------------------------------------------------------------------

@Serializable
data class HskWord(val id: Int, val hanzi: String, val pinyin: String, val translations: List<String>)

fun hskCharacters(file: File): List<String> =
    json.decodeFromString<List<HskWord>>(file.readText())
        .flatMap { it.hanzi.toList() }
        .filter { isHanzi(it) }
        .map { it.toString() }
        .distinct()
        .sorted()

data class WorldDef(val id: String, val name: String, val characters: List<String>)

fun parseWorlds(file: File): List<WorldDef> =
    file.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split("\t")
            require(parts.size == 3) { "worlds file: expected 3 tab-separated fields: $line" }
            WorldDef(parts[0], parts[1], parts[2].map { it.toString() })
        }

@Serializable
data class LlmSentence(
    val character: String,
    val text: String,
    val pinyin: String,
    val english: String,
    val approved: Boolean,
)

@Serializable
data class LlmSentenceMeta(val model: String)

@Serializable
data class LlmSentenceFile(val meta: LlmSentenceMeta, val sentences: List<LlmSentence>)

fun parseSentences(file: File): LlmSentenceFile = json.decodeFromString(file.readText())
