package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Locks the corpus math to the release-gate definitions: honest false rejection and
 * deliberate false acceptance, with `ignored` contact excluded and `unlabeled` records
 * kept out of the rates. Synthetic records only — no pilot data belongs in the repo.
 */
class CorpusAnalyzerTest {

    private val smallPolicy = CorpusGatePolicy(
        minHonest = 2,
        minDeliberate = 2,
        starterCharacters = emptyList(),
        minPhoneSizes = 0,
    )

    @Test
    fun `a corpus that meets every gate passes`() {
        val records = passingCorpus()
        val analysis = analyzeCorpus(records, fileCount = 2)
        assertEquals(300, analysis.records)
        assertEquals(200, analysis.overall.honestScored)
        assertEquals(100, analysis.overall.deliberateScored)
        assertEquals(setOf("360x800", "412x915"), analysis.phoneSizes)
        assertTrue(corpusGateFailures(analysis).isEmpty(), corpusGateFailures(analysis).joinToString())
    }

    @Test
    fun `false rejection and false acceptance are measured against scored attempts`() {
        val records = listOf(
            record("人", HONEST_LABEL, "accepted"),
            record("人", HONEST_LABEL, "accepted-sloppy"),
            record("人", HONEST_LABEL, "rejected-too_far"),
            record("人", HONEST_LABEL, "wrong-order"),
            record("人", DELIBERATE_LABEL, "rejected-wrong_direction"),
            record("人", DELIBERATE_LABEL, "wrong-order"),
            record("人", DELIBERATE_LABEL, "accepted"),
        )
        val analysis = analyzeCorpus(records)
        assertEquals(4, analysis.overall.honestScored)
        assertEquals(2, analysis.overall.honestFalseRejects)
        assertEquals(0.5, analysis.overall.frr)
        assertEquals(3, analysis.overall.deliberateScored)
        assertEquals(1, analysis.overall.deliberateFalseAccepts)
        assertEquals(1.0 / 3.0, analysis.overall.far, 1e-9)
    }

    @Test
    fun `ignored contact is excluded from both denominators`() {
        val records = listOf(
            record("人", HONEST_LABEL, "accepted"),
            record("人", HONEST_LABEL, "accepted"),
            record("人", HONEST_LABEL, "ignored"),
            record("人", DELIBERATE_LABEL, "rejected-too_far"),
            record("人", DELIBERATE_LABEL, "wrong-order"),
            record("人", DELIBERATE_LABEL, "ignored"),
        )
        val analysis = analyzeCorpus(records)
        assertEquals(2, analysis.overall.honestScored)
        assertEquals(1, analysis.overall.honestIgnored)
        assertEquals(2, analysis.overall.deliberateScored)
        assertEquals(1, analysis.overall.deliberateIgnored)
        assertTrue(corpusGateFailures(analysis, smallPolicy).isEmpty())
    }

    @Test
    fun `unlabeled records are reported but do not move the rates`() {
        val records = listOf(
            record("人", HONEST_LABEL, "accepted"),
            record("人", HONEST_LABEL, "accepted"),
            record("人", DELIBERATE_LABEL, "wrong-order"),
            record("人", DELIBERATE_LABEL, "wrong-order"),
            record("人", "unlabeled", "accepted"),
            record("人", "", "rejected-too_far"),
        )
        val analysis = analyzeCorpus(records)
        assertEquals(2, analysis.overall.honestScored)
        assertEquals(2, analysis.overall.deliberateScored)
        assertEquals(2, analysis.overall.unlabeled)
        assertEquals(0.0, analysis.overall.frr)
        assertEquals(0.0, analysis.overall.far)
        assertTrue(corpusGateFailures(analysis, smallPolicy).isEmpty())
    }

    @Test
    fun `unknown labels or results are a gate failure`() {
        val analysis = analyzeCorpus(
            listOf(
                record("人", HONEST_LABEL, "accepted"),
                record("人", DELIBERATE_LABEL, "wrong-order"),
                record("人", "confused", "accepted"),
                record("人", HONEST_LABEL, "teleported"),
                CorpusRecord(character = "", facilitatorLabel = HONEST_LABEL, result = "accepted"),
            ),
        )
        val failures = corpusGateFailures(analysis, smallPolicy)
        assertEquals(3, analysis.overall.invalid)
        assertTrue(failures.any { it.contains("malformed record") }, failures.joinToString())
    }

    @Test
    fun `a missing input directory is reported without a stack-trace surprise`() {
        val missing = Files.createTempDirectory("corpus-test").toFile().resolve("absent")
        val error = assertFailsWith<IllegalArgumentException> { loadCorpus(missing) }
        assertContains(error.message.orEmpty(), "No .jsonl corpus files")
    }

    @Test
    fun `a corpus without two phone sizes fails the gate`() {
        val analysis = analyzeCorpus(passingCorpus().filter { it.screenWidthDp == 360 })
        val failures = corpusGateFailures(analysis)
        assertTrue(failures.any { it.startsWith("phone sizes: 1") }, failures.joinToString())
    }

    @Test
    fun `records without screen dimensions are called out`() {
        val analysis = analyzeCorpus(
            listOf(
                CorpusRecord(character = "人", facilitatorLabel = HONEST_LABEL, result = "accepted"),
                CorpusRecord(character = "人", facilitatorLabel = DELIBERATE_LABEL, result = "wrong-order"),
            ),
        )
        val failures = corpusGateFailures(analysis, smallPolicy.copy(minPhoneSizes = 0))
        assertTrue(failures.any { it.contains("lack screen dimensions") }, failures.joinToString())
    }

    @Test
    fun `exporter-only fields such as points are ignored`() {
        val directory = Files.createTempDirectory("corpus-test").toFile()
        File(directory, "session.jsonl").writeText(
            """{"sessionId":"s","character":"人","facilitatorLabel":"honest","result":"accepted",""" +
                """"screenWidthDp":360,"screenHeightDp":800,""" +
                """"points":[{"x":1.0,"y":2.0,"tMs":10}],"appBuild":"1 (1)"}""" + "\n",
        )
        val (files, records) = loadCorpus(directory)
        assertEquals(1, files)
        assertEquals(1, records.size)
        assertEquals("人", records.single().character)
    }

    @Test
    fun `malformed JSONL names the file and line`() {
        val directory = Files.createTempDirectory("corpus-test").toFile()
        File(directory, "bad.jsonl").writeText("{not json}\n")
        val error = assertFailsWith<IllegalArgumentException> { loadCorpus(directory) }
        assertContains(error.message.orEmpty(), "bad.jsonl:1")
    }

    @Test
    fun `report shows pass or the blocking gaps`() {
        val passing = analyzeCorpus(passingCorpus())
        assertContains(renderCorpusReport(passing, emptyList()), "**Gate status: PASS**")

        val failing = analyzeCorpus(listOf(record("人", HONEST_LABEL, "rejected-too_far")))
        val report = renderCorpusReport(failing, corpusGateFailures(failing))
        assertContains(report, "**Gate status: NOT YET**")
        assertContains(report, "## Blocking gaps")
    }

    private fun passingCorpus(): List<CorpusRecord> {
        val characters = listOf("人", "大", "天")
        val sizes = listOf(360 to 800, 412 to 915)
        val records = mutableListOf<CorpusRecord>()
        repeat(200) { index ->
            records += record(characters[index % 3], HONEST_LABEL, "accepted", sizes[index % 2])
        }
        repeat(100) { index ->
            records += record(
                characters[index % 3],
                DELIBERATE_LABEL,
                if (index % 2 == 0) "rejected-wrong_direction" else "wrong-order",
                sizes[index % 2],
            )
        }
        return records
    }

    private fun record(
        character: String,
        label: String,
        result: String,
        size: Pair<Int, Int> = 360 to 800,
    ) = CorpusRecord(
        character = character,
        facilitatorLabel = label,
        result = result,
        screenWidthDp = size.first,
        screenHeightDp = size.second,
    )
}
