package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

/**
 * Checks the facilitator-led M4.1 pilot worksheet against the release gates
 * (docs/milestones/m4.1-pilot-protocol.md).
 *
 * The pilot deliberately ships **no telemetry**: the signed APK's exporter remains a
 * no-op. A facilitator records the small number of facts the gate needs on a
 * pseudonymous CSV, and this tool turns it into the gate report. That keeps minors' data
 * out of the app, out of the network, and out of the repository.
 */
data class PilotResult(
    val participantId: String,
    val guardianConsent: Boolean?,
    val teenAssent: Boolean?,
    val firstQuestDate: LocalDate?,
    val firstQuestUnaided: Boolean?,
    val coreDurationSeconds: Int?,
    val fairness: Int?,
    val nextDayDate: LocalDate?,
    val nextDayRecallCount: Int?,
    val thirdSessionDate: LocalDate?,
    val incidents: String,
    val notes: String,
) {
    val daysToThirdSession: Long?
        get() = if (firstQuestDate != null && thirdSessionDate != null) {
            ChronoUnit.DAYS.between(firstQuestDate, thirdSessionDate)
        } else {
            null
        }

    val complete: Boolean
        get() = guardianConsent == true &&
            teenAssent == true &&
            firstQuestDate != null &&
            firstQuestUnaided != null &&
            coreDurationSeconds != null &&
            fairness != null &&
            nextDayDate != null &&
            nextDayRecallCount != null &&
            thirdSessionDate != null

    val incompleteReason: String
        get() = buildList {
            if (guardianConsent != true) add("guardian consent not granted")
            if (teenAssent != true) add("teen assent not granted")
            if (firstQuestDate == null) add("first-quest date missing")
            if (firstQuestUnaided == null) add("unaided flag missing")
            if (coreDurationSeconds == null) add("core duration missing")
            if (fairness == null) add("fairness rating missing")
            if (nextDayDate == null) add("next-day date missing")
            if (nextDayRecallCount == null) add("next-day recall missing")
            if (thirdSessionDate == null) add("third-session date missing")
        }.joinToString(", ")
}

data class PilotGatePolicy(
    val minParticipants: Int = 5,
    val maxParticipants: Int = 8,
    val minRate: Double = 0.8,
    val maxMedianCoreSeconds: Int = 600,
    val maxDaysToThirdSession: Long = 4,
)

data class PilotAnalysis(
    val rows: List<PilotResult>,
    val participants: List<PilotResult>,
    val incomplete: List<PilotResult>,
) {
    val n: Int get() = participants.size
    val unaidedRate: Double get() = rate(participants.count { it.firstQuestUnaided == true }, n)
    val fairnessRate: Double get() = rate(participants.count { (it.fairness ?: 0) >= 4 }, n)
    val recallRate: Double get() = rate(participants.count { (it.nextDayRecallCount ?: 0) >= 2 }, n)
    val medianCoreSeconds: Double? get() = median(participants.mapNotNull { it.coreDurationSeconds })
    val incidentParticipants: List<String>
        get() = participants.filter { it.incidents.isNotBlank() }.map { it.participantId }
}

fun loadPilotResults(file: File): List<PilotResult> {
    require(file.isFile) {
        "Pilot worksheet not found: ${file.path}. Copy data/pilot/pilot-results-template.csv first."
    }
    val rows = parseCsv(file.readText())
    require(rows.isNotEmpty()) { "Pilot worksheet is empty: ${file.path}" }

    val header = rows.first().map { it.trim() }
    val missing = REQUIRED_COLUMNS - header.toSet()
    require(missing.isEmpty()) {
        "Pilot worksheet is missing column(s): ${missing.joinToString(", ")}"
    }
    val index = header.withIndex().associate { (position, name) -> name to position }

    return rows.drop(1).mapIndexed { offset, row ->
        val line = offset + 2 // header is line 1
        fun cell(name: String): String = row.getOrNull(index.getValue(name))?.trim().orEmpty()
        fun optionalCell(name: String): String? = cell(name).ifBlank { null }
        fun date(name: String): LocalDate? = optionalCell(name)?.let {
            try {
                LocalDate.parse(it)
            } catch (error: DateTimeParseException) {
                throw IllegalArgumentException("$line: ${name} must be an ISO date (yyyy-mm-dd): $it", error)
            }
        }
        fun int(name: String): Int? = optionalCell(name)?.let {
            it.toIntOrNull()
                ?: throw IllegalArgumentException("$line: ${name} must be a whole number: $it")
        }

        PilotResult(
            participantId = cell("participantId").also {
                require(it.isNotBlank()) { "$line: participantId is blank" }
            },
            guardianConsent = yesNo(cell("guardianConsent"), "guardianConsent", line),
            teenAssent = yesNo(cell("teenAssent"), "teenAssent", line),
            firstQuestDate = date("firstQuestDate"),
            firstQuestUnaided = yesNo(cell("firstQuestUnaided"), "firstQuestUnaided", line),
            coreDurationSeconds = int("coreDurationSeconds")?.also {
                require(it > 0) { "$line: coreDurationSeconds must be > 0" }
            },
            fairness = int("fairness1to5")?.also {
                require(it in 1..5) { "$line: fairness1to5 must be 1..5" }
            },
            nextDayDate = date("nextDayDate"),
            nextDayRecallCount = int("nextDayRecallCount")?.also {
                require(it in 0..3) { "$line: nextDayRecallCount must be 0..3" }
            },
            thirdSessionDate = date("thirdSessionDate"),
            incidents = cell("incidents"),
            notes = cell("notes"),
        )
    }
}

fun analyzePilotResults(rows: List<PilotResult>): PilotAnalysis = PilotAnalysis(
    rows = rows,
    participants = rows.filter { it.complete },
    incomplete = rows.filterNot { it.complete },
)

fun pilotGateFailures(
    analysis: PilotAnalysis,
    policy: PilotGatePolicy = PilotGatePolicy(),
): List<String> {
    val failures = mutableListOf<String>()
    if (analysis.n !in policy.minParticipants..policy.maxParticipants) {
        failures += "complete participants: ${analysis.n} " +
            "(need ${policy.minParticipants}..${policy.maxParticipants})"
    }
    if (analysis.n > 0 && analysis.unaidedRate < policy.minRate) {
        failures += "finished unaided ${percent(analysis.unaidedRate)} (need >= ${percent(policy.minRate)})"
    }
    val median = analysis.medianCoreSeconds
    if (median == null) {
        failures += "median core duration is unknown"
    } else if (median > policy.maxMedianCoreSeconds) {
        failures += "median core duration ${seconds(median)} (need <= ${seconds(policy.maxMedianCoreSeconds.toDouble())})"
    }
    if (analysis.n > 0 && analysis.fairnessRate < policy.minRate) {
        failures += "fairness >= 4/5 ${percent(analysis.fairnessRate)} (need >= ${percent(policy.minRate)})"
    }
    if (analysis.n > 0 && analysis.recallRate < policy.minRate) {
        failures += "next-day recall >= 2/3 ${percent(analysis.recallRate)} (need >= ${percent(policy.minRate)})"
    }
    for (participant in analysis.participants) {
        val first = participant.firstQuestDate
        val next = participant.nextDayDate
        if (first != null && next != null && next != first.plusDays(1)) {
            failures += "${participant.participantId}: next-day recall was not the calendar day after the first quest"
        }
        val days = participant.daysToThirdSession
        if (days == null || days !in 1..policy.maxDaysToThirdSession) {
            failures += "${participant.participantId}: third session was not 1..${policy.maxDaysToThirdSession} days after the first quest"
        }
    }
    if (analysis.incidentParticipants.isNotEmpty()) {
        failures += "incident reported for ${analysis.incidentParticipants.joinToString(", ")} " +
            "(no crash, lost progress, duplicate reward, or blocked core journey)"
    }
    return failures
}

fun renderPilotReport(
    analysis: PilotAnalysis,
    failures: List<String>,
    policy: PilotGatePolicy = PilotGatePolicy(),
): String = buildString {
    appendLine("# M4.1 pilot report")
    appendLine()
    appendLine("Generated from the pseudonymous facilitator worksheet. No app telemetry is used.")
    appendLine()
    appendLine("**Gate status: ${if (failures.isEmpty()) "PASS" else "NOT YET"}**")
    appendLine()
    appendLine("| Measure | Value | Gate |")
    appendLine("|---|---|---|")
    appendLine("| Complete participants | ${analysis.n} | ${policy.minParticipants}..${policy.maxParticipants} |")
    appendLine("| Finished unaided | ${percent(analysis.unaidedRate)} | >= ${percent(policy.minRate)} |")
    appendLine(
        "| Median core duration | ${analysis.medianCoreSeconds?.let(::seconds) ?: "—"} | " +
            "<= ${seconds(policy.maxMedianCoreSeconds.toDouble())} |",
    )
    appendLine("| Fairness >= 4/5 | ${percent(analysis.fairnessRate)} | >= ${percent(policy.minRate)} |")
    appendLine("| Next-day recall >= 2/3 | ${percent(analysis.recallRate)} | >= ${percent(policy.minRate)} |")
    appendLine("| Incidents | ${analysis.incidentParticipants.size} | 0 |")
    appendLine()
    if (failures.isNotEmpty()) {
        appendLine("## Blocking gaps")
        appendLine()
        failures.forEach { appendLine("- $it") }
        appendLine()
    }
    appendLine("## Participants")
    appendLine()
    appendLine("| ID | First quest | Unaided | Duration | Fairness | Recall | Third session | Incidents |")
    appendLine("|---|---|---|---|---|---|---|---|")
    for (participant in analysis.participants) {
        appendLine(
            "| ${participant.participantId} | ${participant.firstQuestDate} | " +
                "${if (participant.firstQuestUnaided == true) "yes" else "no"} | " +
                "${participant.coreDurationSeconds?.let { seconds(it.toDouble()) } ?: "—"} | " +
                "${participant.fairness}/5 | " +
                "${participant.nextDayRecallCount}/3 | " +
                "${participant.thirdSessionDate} (+${participant.daysToThirdSession}d) | " +
                "${participant.incidents.ifBlank { "none" }} |",
        )
    }
    if (analysis.incomplete.isNotEmpty()) {
        appendLine()
        appendLine("## Not counted (withdrawn or incomplete)")
        appendLine()
        appendLine("| ID | Reason | Notes |")
        appendLine("|---|---|---|")
        for (participant in analysis.incomplete) {
            appendLine(
                "| ${participant.participantId} | ${participant.incompleteReason} | " +
                    "${participant.notes.ifBlank { "—" }} |",
            )
        }
    }
    appendLine()
    appendLine("Rates use complete participants as the denominator. A withdrawal is not a")
    appendLine("failure, but it does not count toward the 5–8 participant requirement.")
}

// ---------------------------------------------------------------------------
// CSV
// ---------------------------------------------------------------------------

private val REQUIRED_COLUMNS = listOf(
    "participantId",
    "guardianConsent",
    "teenAssent",
    "firstQuestDate",
    "firstQuestUnaided",
    "coreDurationSeconds",
    "fairness1to5",
    "nextDayDate",
    "nextDayRecallCount",
    "thirdSessionDate",
    "incidents",
    "notes",
)

private fun yesNo(value: String, column: String, line: Int): Boolean? = when (value.trim().lowercase()) {
    "yes", "y", "true" -> true
    "no", "n", "false" -> false
    "" -> null
    else -> throw IllegalArgumentException("$line: $column must be yes or no: $value")
}

/** Minimal RFC 4180 parser: quoted fields, escaped quotes, and newlines inside quotes. */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            inQuotes && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                field.append('"')
                index++
            }
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                row += field.toString()
                field.clear()
            }
            (char == '\n' || char == '\r') && !inQuotes -> {
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                row += field.toString()
                field.clear()
                if (row.any { it.isNotBlank() }) rows += row
                row = mutableListOf()
            }
            else -> field.append(char)
        }
        index++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
    }
    return rows
}

private fun rate(numerator: Int, denominator: Int): Double =
    if (denominator == 0) 0.0 else numerator.toDouble() / denominator

private fun median(values: List<Int>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle].toDouble()
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}

private fun percent(value: Double): String = "%.1f%%".format(value * 100)

private fun seconds(value: Double): String {
    val rounded = value.toInt()
    return if (rounded < 60) "${rounded}s" else "${rounded / 60}m ${rounded % 60}s"
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

private data class PilotOptions(
    val input: String = "data/pilot/pilot-results.csv",
    val output: String = "data/pilot/pilot-report.md",
    val requireGate: Boolean = false,
    val policy: PilotGatePolicy = PilotGatePolicy(),
)

private fun parsePilotOptions(args: Array<String>): PilotOptions {
    var options = PilotOptions()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--input" -> options = options.copy(input = args[++i])
            "--output" -> options = options.copy(output = args[++i])
            "--require-gate" -> options = options.copy(requireGate = true)
            else -> error("Unknown argument: $arg")
        }
        i++
    }
    return options
}

fun main(args: Array<String>) = pilotMain(args)

/** Entry point for `checkPilotResults`: same report, but a gate failure exits non-zero. */
fun pilotMain(args: Array<String>) {
    val options = parsePilotOptions(args)
    val root = File(".").canonicalFile
    val input = resolveFromRoot(root, options.input)
    val output = resolveFromRoot(root, options.output)

    val rows = loadPilotResults(input)
    val analysis = analyzePilotResults(rows)
    val failures = pilotGateFailures(analysis, options.policy)

    output.parentFile.mkdirs()
    output.writeText(renderPilotReport(analysis, failures, options.policy) + "\n")

    println(
        "Pilot: ${analysis.n} complete participant(s), " +
            "unaided ${percent(analysis.unaidedRate)}, " +
            "median ${analysis.medianCoreSeconds?.let(::seconds) ?: "unknown"}, " +
            "fairness ${percent(analysis.fairnessRate)}, recall ${percent(analysis.recallRate)}.",
    )
    println("Report: ${if (output.startsWith(root)) output.relativeTo(root).path else output.path}")
    if (failures.isNotEmpty()) {
        failures.forEach { System.err.println("GATE: $it") }
        if (options.requireGate) exitProcess(1)
    }
}
