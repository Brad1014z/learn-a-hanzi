package io.github.brad1014z.hanzi.ingest

import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.Serializable

/**
 * Turns the debug build's local JSONL stroke exports into the M4.1 corpus evidence
 * (docs/milestones/m4.1-release-gates.md): at least 200 honest strokes and 100
 * deliberate direction/order errors, across the starter characters and two phone sizes,
 * with honest false rejection < 5% and deliberate acceptance < 1%.
 *
 * Ground truth comes from the facilitator label the app already writes:
 *
 *   - `honest`                 → the stroke should be accepted
 *   - `wrong-direction-order`  → the stroke should be rejected or flagged as wrong order
 *   - `unlabeled`              → excluded from the rates (and reported)
 *
 * `ignored` attempts (accidental contact below the minimum stroke length) are not
 * real attempts, so they are excluded from both denominators.
 *
 * The raw exports are never committed; the generated report is. See data/corpus/README.md.
 */
@Serializable
data class CorpusRecord(
    val sessionId: String = "",
    val appBuild: String = "",
    val deviceProfile: String = "",
    val screenWidthDp: Int? = null,
    val screenHeightDp: Int? = null,
    val character: String = "",
    val expectedStrokeIndex: Int = 0,
    val retryCount: Int = 0,
    val facilitatorLabel: String = "",
    val result: String = "",
) {
    val sizeKey: String
        get() = if (screenWidthDp != null && screenHeightDp != null) {
            "${screenWidthDp}x${screenHeightDp}"
        } else {
            "unknown"
        }
}

const val HONEST_LABEL = "honest"
const val DELIBERATE_LABEL = "wrong-direction-order"

private val ACCEPTED_RESULTS = setOf("accepted", "accepted-sloppy")

/** Counters for one slice of the corpus (all records, one character, or one phone size). */
data class CorpusTally(
    val honest: Int = 0,
    val honestIgnored: Int = 0,
    val honestFalseRejects: Int = 0,
    val deliberate: Int = 0,
    val deliberateIgnored: Int = 0,
    val deliberateFalseAccepts: Int = 0,
    val unlabeled: Int = 0,
    val invalid: Int = 0,
) {
    val honestScored: Int get() = honest - honestIgnored
    val deliberateScored: Int get() = deliberate - deliberateIgnored

    /** Honest strokes the grader refused (reject or wrong-order). */
    val frr: Double get() = ratio(honestFalseRejects, honestScored)

    /** Deliberate errors the grader accepted. */
    val far: Double get() = ratio(deliberateFalseAccepts, deliberateScored)

    fun add(record: CorpusRecord): CorpusTally {
        if (!record.isCountable()) return copy(invalid = invalid + 1)
        return when (record.facilitatorLabel) {
            HONEST_LABEL -> when (record.result) {
                "ignored" -> copy(honest = honest + 1, honestIgnored = honestIgnored + 1)
                in ACCEPTED_RESULTS -> copy(honest = honest + 1)
                else -> copy(honest = honest + 1, honestFalseRejects = honestFalseRejects + 1)
            }
            DELIBERATE_LABEL -> when (record.result) {
                "ignored" -> copy(deliberate = deliberate + 1, deliberateIgnored = deliberateIgnored + 1)
                in ACCEPTED_RESULTS -> copy(
                    deliberate = deliberate + 1,
                    deliberateFalseAccepts = deliberateFalseAccepts + 1,
                )
                else -> copy(deliberate = deliberate + 1)
            }
            else -> copy(unlabeled = unlabeled + 1)
        }
    }
}

private fun CorpusRecord.isCountable(): Boolean =
    character.isNotBlank() &&
        result.isKnownResult() &&
        facilitatorLabel in setOf(HONEST_LABEL, DELIBERATE_LABEL, "unlabeled", "")

private fun String.isKnownResult(): Boolean =
    this in ACCEPTED_RESULTS || this == "wrong-order" || this == "ignored" || startsWith("rejected-")

data class CorpusAnalysis(
    val files: Int,
    val records: Int,
    val overall: CorpusTally,
    val byCharacter: Map<String, CorpusTally>,
    val byPhoneSize: Map<String, CorpusTally>,
) {
    val phoneSizes: Set<String> get() = byPhoneSize.keys - "unknown"
}

data class CorpusGatePolicy(
    val minHonest: Int = 200,
    val minDeliberate: Int = 100,
    val maxFrr: Double = 0.05,
    val maxFar: Double = 0.01,
    val starterCharacters: List<String> = listOf("人", "大", "天"),
    val minPhoneSizes: Int = 2,
)

fun loadCorpus(input: File): Pair<Int, List<CorpusRecord>> {
    val files = when {
        input.isDirectory -> input.listFiles { file -> file.isFile && file.extension == "jsonl" }
            ?.sortedBy { it.name }
            .orEmpty()
        input.isFile -> listOf(input)
        else -> emptyList()
    }
    require(files.isNotEmpty()) {
        "No .jsonl corpus files at ${input.path}. Pull them from the device first (data/corpus/README.md)."
    }
    val records = mutableListOf<CorpusRecord>()
    for (file in files) {
        file.useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (line.isBlank()) return@forEachIndexed
                try {
                    records += json.decodeFromString<CorpusRecord>(line)
                } catch (error: Exception) {
                    throw IllegalArgumentException(
                        "${file.name}:${index + 1}: malformed corpus record: ${error.message}",
                        error,
                    )
                }
            }
        }
    }
    return files.size to records
}

fun analyzeCorpus(records: List<CorpusRecord>, fileCount: Int = 1): CorpusAnalysis {
    var overall = CorpusTally()
    val byCharacter = sortedMapOf<String, CorpusTally>()
    val byPhoneSize = sortedMapOf<String, CorpusTally>()
    for (record in records) {
        overall = overall.add(record)
        if (!record.isCountable()) continue
        byCharacter[record.character] = (byCharacter[record.character] ?: CorpusTally()).add(record)
        byPhoneSize[record.sizeKey] = (byPhoneSize[record.sizeKey] ?: CorpusTally()).add(record)
    }
    return CorpusAnalysis(
        files = fileCount,
        records = records.size,
        overall = overall,
        byCharacter = byCharacter,
        byPhoneSize = byPhoneSize,
    )
}

fun corpusGateFailures(
    analysis: CorpusAnalysis,
    policy: CorpusGatePolicy = CorpusGatePolicy(),
): List<String> {
    val failures = mutableListOf<String>()
    val overall = analysis.overall

    if (overall.honestScored < policy.minHonest) {
        failures += "honest strokes: ${overall.honestScored} (need >= ${policy.minHonest})"
    }
    if (overall.deliberateScored < policy.minDeliberate) {
        failures += "deliberate errors: ${overall.deliberateScored} (need >= ${policy.minDeliberate})"
    }
    if (overall.frr >= policy.maxFrr) {
        failures += "honest false rejection ${percent(overall.frr)} (need < ${percent(policy.maxFrr)})"
    }
    if (overall.far >= policy.maxFar) {
        failures += "deliberate acceptance ${percent(overall.far)} (need < ${percent(policy.maxFar)})"
    }
    if (overall.invalid > 0) {
        failures += "${overall.invalid} malformed record(s): unknown label, unknown result, or blank character"
    }
    if (analysis.phoneSizes.size < policy.minPhoneSizes) {
        failures += "phone sizes: ${analysis.phoneSizes.size} (need >= ${policy.minPhoneSizes}; " +
            "saw ${analysis.phoneSizes.ifEmpty { setOf("none") }.joinToString(", ")})"
    }
    for (character in policy.starterCharacters) {
        val tally = analysis.byCharacter[character]
        if (tally == null || tally.honestScored == 0) {
            failures += "starter character $character has no honest strokes"
        }
        if (tally == null || tally.deliberateScored == 0) {
            failures += "starter character $character has no deliberate errors"
        }
    }
    for ((size, tally) in analysis.byPhoneSize) {
        if (size == "unknown") {
            failures += "${tally.honest + tally.deliberate} record(s) lack screen dimensions " +
                "(rebuild the pilot APK after this field was added)"
            continue
        }
        if (tally.honestScored == 0) failures += "phone size $size has no honest strokes"
        if (tally.deliberateScored == 0) failures += "phone size $size has no deliberate errors"
    }
    return failures
}

fun renderCorpusReport(
    analysis: CorpusAnalysis,
    failures: List<String>,
    policy: CorpusGatePolicy = CorpusGatePolicy(),
): String = buildString {
    appendLine("# Pilot stroke corpus report")
    appendLine()
    appendLine("Generated by `./gradlew :data-ingest:analyzePilotCorpus` from the local JSONL")
    appendLine("exports. Raw exports are not committed; this report is the evidence.")
    appendLine()
    appendLine("**Gate status: ${if (failures.isEmpty()) "PASS" else "NOT YET"}**")
    appendLine()
    appendLine("| Measure | Value | Gate |")
    appendLine("|---|---|---|")
    appendLine("| Files / records | ${analysis.files} / ${analysis.records} | — |")
    appendLine("| Honest strokes (scored) | ${analysis.overall.honestScored} | >= ${policy.minHonest} |")
    appendLine("| Honest false rejection | ${rate(analysis.overall.honestFalseRejects, analysis.overall.honestScored)} | < ${percent(policy.maxFrr)} |")
    appendLine("| Deliberate errors (scored) | ${analysis.overall.deliberateScored} | >= ${policy.minDeliberate} |")
    appendLine("| Deliberate acceptance | ${rate(analysis.overall.deliberateFalseAccepts, analysis.overall.deliberateScored)} | < ${percent(policy.maxFar)} |")
    appendLine("| Phone sizes | ${analysis.phoneSizes.size} | >= ${policy.minPhoneSizes} |")
    appendLine("| Unlabeled (excluded) | ${analysis.overall.unlabeled} | — |")
    appendLine("| Malformed (excluded) | ${analysis.overall.invalid} | 0 |")
    appendLine("| Ignored contact (excluded) | ${analysis.overall.honestIgnored + analysis.overall.deliberateIgnored} | — |")
    appendLine()
    if (failures.isNotEmpty()) {
        appendLine("## Blocking gaps")
        appendLine()
        failures.forEach { appendLine("- $it") }
        appendLine()
    }
    appendLine("## By character")
    appendLine()
    appendLine("| Character | Honest | FRR | Deliberate | FAR |")
    appendLine("|---|---|---|---|---|")
    for ((character, tally) in analysis.byCharacter) {
        appendLine(
            "| $character | ${tally.honestScored} | " +
                "${rate(tally.honestFalseRejects, tally.honestScored)} | " +
                "${tally.deliberateScored} | " +
                "${rate(tally.deliberateFalseAccepts, tally.deliberateScored)} |",
        )
    }
    appendLine()
    appendLine("## By phone size (dp)")
    appendLine()
    appendLine("| Size | Honest | FRR | Deliberate | FAR |")
    appendLine("|---|---|---|---|---|")
    for ((size, tally) in analysis.byPhoneSize) {
        appendLine(
            "| $size | ${tally.honestScored} | " +
                "${rate(tally.honestFalseRejects, tally.honestScored)} | " +
                "${tally.deliberateScored} | " +
                "${rate(tally.deliberateFalseAccepts, tally.deliberateScored)} |",
        )
    }
    appendLine()
    appendLine("FRR = honest strokes rejected or flagged wrong-order. FAR = deliberate errors")
    appendLine("accepted. `ignored` contact is excluded from both denominators; `unlabeled`")
    appendLine("records are excluded from the rates.")
}

private fun ratio(numerator: Int, denominator: Int): Double =
    if (denominator == 0) 0.0 else numerator.toDouble() / denominator

private fun percent(value: Double): String = "%.2f%%".format(value * 100)

private fun rate(numerator: Int, denominator: Int): String =
    if (denominator == 0) "—" else percent(numerator.toDouble() / denominator)

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

private data class CorpusOptions(
    val input: String = "data/corpus/raw",
    val output: String = "data/corpus/corpus-report.md",
    val requireGate: Boolean = false,
    val policy: CorpusGatePolicy = CorpusGatePolicy(),
)

private fun parseCorpusOptions(args: Array<String>): CorpusOptions {
    var options = CorpusOptions()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--input" -> options = options.copy(input = args[++i])
            "--output" -> options = options.copy(output = args[++i])
            "--require-gate" -> options = options.copy(requireGate = true)
            "--min-honest" -> options = options.copy(
                policy = options.policy.copy(minHonest = args[++i].toInt()),
            )
            "--min-deliberate" -> options = options.copy(
                policy = options.policy.copy(minDeliberate = args[++i].toInt()),
            )
            else -> error("Unknown argument: $arg")
        }
        i++
    }
    return options
}

fun main(args: Array<String>) = corpusMain(args)

/** Entry point for `checkPilotCorpus`: same report, but a gate failure exits non-zero. */
fun corpusMain(args: Array<String>) {
    val options = parseCorpusOptions(args)
    val root = File(".").canonicalFile
    val input = resolveFromRoot(root, options.input)
    val output = resolveFromRoot(root, options.output)

    val (fileCount, records) = loadCorpus(input)
    val analysis = analyzeCorpus(records, fileCount)
    val failures = corpusGateFailures(analysis, options.policy)

    output.parentFile.mkdirs()
    output.writeText(renderCorpusReport(analysis, failures, options.policy) + "\n")

    println(
        "Corpus: ${analysis.records} records from $fileCount file(s) — " +
            "${analysis.overall.honestScored} honest (FRR ${percent(analysis.overall.frr)}), " +
            "${analysis.overall.deliberateScored} deliberate (FAR ${percent(analysis.overall.far)}).",
    )
    println("Report: ${if (output.startsWith(root)) output.relativeTo(root).path else output.path}")
    if (failures.isNotEmpty()) {
        failures.forEach { System.err.println("GATE: $it") }
        if (options.requireGate) exitProcess(1)
    }
}
