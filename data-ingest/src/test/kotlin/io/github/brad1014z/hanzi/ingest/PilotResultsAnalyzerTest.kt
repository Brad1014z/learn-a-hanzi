package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Locks the facilitator worksheet to the M4.1 pilot gates. The pilot has no telemetry,
 * so these definitions *are* the measurement: unaided completion, core duration,
 * fairness, next-day recall, third-session timing, and incidents.
 */
class PilotResultsAnalyzerTest {

    @Test
    fun `CSV parsing handles quoted commas, escaped quotes, and embedded newlines`() {
        val rows = parseCsv("a,b\n\"x,y\",\"line1\nline2\"\n\"he said \"\"hi\"\"\",z\n")
        assertEquals(
            listOf(
                listOf("a", "b"),
                listOf("x,y", "line1\nline2"),
                listOf("he said \"hi\"", "z"),
            ),
            rows,
        )
    }

    @Test
    fun `five complete participants pass every gate`() {
        val analysis = analyzePilotResults(loadRows(passingCsv(5)))
        assertEquals(5, analysis.n)
        assertEquals(0.0, analysis.unaidedRate - 1.0, 1e-9)
        assertEquals(540.0, analysis.medianCoreSeconds)
        assertEquals(1.0, analysis.fairnessRate, 1e-9)
        assertEquals(1.0, analysis.recallRate, 1e-9)
        assertTrue(pilotGateFailures(analysis).isEmpty(), pilotGateFailures(analysis).joinToString())
    }

    @Test
    fun `median duration handles even and odd counts`() {
        val odd = analyzePilotResults(loadRows(passingCsv(5)))
        assertEquals(540.0, odd.medianCoreSeconds)

        val even = analyzePilotResults(
            loadRows(
                header +
                    row("P1", duration = 480) +
                    row("P2", duration = 540) +
                    row("P3", duration = 600) +
                    row("P4", duration = 660),
            ),
        )
        assertEquals(570.0, even.medianCoreSeconds)
    }

    @Test
    fun `less than eighty percent unaided fails`() {
        val csv = header +
            row("P1", unaided = "yes") +
            row("P2", unaided = "yes") +
            row("P3", unaided = "yes") +
            row("P4", unaided = "no") +
            row("P5", unaided = "no")
        val failures = pilotGateFailures(analyzePilotResults(loadRows(csv)))
        assertTrue(failures.any { it.contains("finished unaided") }, failures.joinToString())
    }

    @Test
    fun `a median over ten minutes fails`() {
        val csv = header +
            row("P1", duration = 600) +
            row("P2", duration = 660) +
            row("P3", duration = 720) +
            row("P4", duration = 780) +
            row("P5", duration = 840)
        val failures = pilotGateFailures(analyzePilotResults(loadRows(csv)))
        assertTrue(failures.any { it.contains("median core duration") }, failures.joinToString())
    }

    @Test
    fun `fairness and recall below eighty percent fail`() {
        val csv = header +
            row("P1", fairness = 5, recall = 3) +
            row("P2", fairness = 4, recall = 2) +
            row("P3", fairness = 4, recall = 2) +
            row("P4", fairness = 3, recall = 1) +
            row("P5", fairness = 2, recall = 1)
        val failures = pilotGateFailures(analyzePilotResults(loadRows(csv)))
        assertTrue(failures.any { it.contains("fairness") }, failures.joinToString())
        assertTrue(failures.any { it.contains("next-day recall") }, failures.joinToString())
    }

    @Test
    fun `eighty percent and ten minutes are inclusive boundaries`() {
        val csv = header +
            row("P1", unaided = "yes", duration = 600, fairness = 4, recall = 2) +
            row("P2", unaided = "yes", duration = 600, fairness = 4, recall = 2) +
            row("P3", unaided = "yes", duration = 600, fairness = 4, recall = 2) +
            row("P4", unaided = "yes", duration = 600, fairness = 4, recall = 2) +
            row("P5", unaided = "no", duration = 600, fairness = 3, recall = 1)
        val failures = pilotGateFailures(analyzePilotResults(loadRows(csv)))
        assertTrue(failures.isEmpty(), failures.joinToString())
    }

    @Test
    fun `incomplete rows are reported and do not count as participants`() {
        val csv = header +
            row("P1") + row("P2") + row("P3") + row("P4") +
            row("P5", thirdSession = "")
        val analysis = analyzePilotResults(loadRows(csv))
        assertEquals(4, analysis.n)
        assertEquals(listOf("P5"), analysis.incomplete.map { it.participantId })
        assertContains(analysis.incomplete.single().incompleteReason, "third-session date missing")
        assertTrue(pilotGateFailures(analysis).any { it.contains("complete participants: 4") })
    }

    @Test
    fun `a withdrawal is reported but does not become a failure`() {
        val csv = header +
            row("P1") + row("P2") + row("P3") + row("P4") + row("P5") +
            row("P6", guardianConsent = "no")
        val analysis = analyzePilotResults(loadRows(csv))
        assertEquals(5, analysis.n)
        assertEquals(listOf("P6"), analysis.incomplete.map { it.participantId })
        assertTrue(pilotGateFailures(analysis).isEmpty(), pilotGateFailures(analysis).joinToString())
    }

    @Test
    fun `an incident fails the no-crash-no-lost-progress gate`() {
        val csv = header +
            row("P1") + row("P2") + row("P3") + row("P4") +
            row("P5", incidents = "progress reset after chest")
        val failures = pilotGateFailures(analyzePilotResults(loadRows(csv)))
        assertTrue(failures.any { it.contains("incident reported for P5") }, failures.joinToString())
    }

    @Test
    fun `next-day recall must be the next calendar day and the third session within four days`() {
        val csv = header +
            row("P1") + row("P2") + row("P3") + row("P4") +
            row("P5", nextDay = "2026-10-28", thirdSession = "2026-11-02")
        val failures = pilotGateFailures(analyzePilotResults(loadRows(csv)))
        assertTrue(failures.any { it.contains("P5: next-day recall") }, failures.joinToString())
        assertTrue(failures.any { it.contains("P5: third session") }, failures.joinToString())
    }

    @Test
    fun `out-of-range values are rejected with the line number`() {
        val csv = header + row("P1", fairness = 6)
        val error = assertFailsWith<IllegalArgumentException> { loadRows(csv) }
        assertContains(error.message.orEmpty(), "fairness1to5 must be 1..5")
    }

    @Test
    fun `a missing worksheet gives a copy-the-template message`() {
        val missing = Files.createTempDirectory("pilot-test").toFile().resolve("absent.csv")
        val error = assertFailsWith<IllegalArgumentException> { loadPilotResults(missing) }
        assertContains(error.message.orEmpty(), "pilot-results-template.csv")
    }

    @Test
    fun `a missing column is reported by name`() {
        val csv = header.replace("fairness1to5,", "")
        val error = assertFailsWith<IllegalArgumentException> { loadRows(csv) }
        assertContains(error.message.orEmpty(), "fairness1to5")
    }

    private fun loadRows(csv: String): List<PilotResult> {
        val file = File(Files.createTempDirectory("pilot-test").toFile(), "pilot-results.csv")
        file.writeText(csv)
        return loadPilotResults(file)
    }

    private fun passingCsv(count: Int): String {
        val durations = listOf(480, 540, 600, 420, 660)
        return header + (1..count).joinToString("") { index ->
            row(
                "P$index",
                duration = durations[(index - 1) % durations.size],
                fairness = if (index == 5) 4 else 5,
                recall = if (index == 5) 2 else 3,
            )
        }
    }

    private fun row(
        id: String,
        guardianConsent: String = "yes",
        teenAssent: String = "yes",
        firstQuest: String = "2026-10-26",
        unaided: String = "yes",
        duration: Int = 480,
        fairness: Int = 4,
        nextDay: String = "2026-10-27",
        recall: Int = 2,
        thirdSession: String = "2026-10-28",
        incidents: String = "",
        notes: String = "",
    ): String = "$id,$guardianConsent,$teenAssent,$firstQuest,$unaided,$duration,$fairness," +
        "$nextDay,$recall,$thirdSession,\"$incidents\",\"$notes\"\n"

    private val header =
        "participantId,guardianConsent,teenAssent,firstQuestDate,firstQuestUnaided," +
            "coreDurationSeconds,fairness1to5,nextDayDate,nextDayRecallCount,thirdSessionDate," +
            "incidents,notes\n"
}
