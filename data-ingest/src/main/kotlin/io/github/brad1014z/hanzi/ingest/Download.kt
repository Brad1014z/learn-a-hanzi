package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

/**
 * Downloads the pinned raw sources into data/raw/ and verifies every file against
 * data/sources.lock (spec 02: pinned snapshots; fail loudly on drift). Re-pinning is a
 * deliberate act: update the URL/lock together in one reviewed commit.
 */
private const val MMAH_COMMIT = "bddc96d41bef78427ed0e034e9f7e31d71fd1b92"

val PINNED_SOURCES = mapOf(
    "graphics.txt" to "https://raw.githubusercontent.com/skishore/makemeahanzi/$MMAH_COMMIT/graphics.txt",
    "dictionary.txt" to "https://raw.githubusercontent.com/skishore/makemeahanzi/$MMAH_COMMIT/dictionary.txt",
    "cedict.txt.gz" to "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz",
    "Unihan.zip" to "https://www.unicode.org/Public/16.0.0/ucd/Unihan.zip",
    "cmn_sentences_detailed.tsv.bz2" to
        "https://downloads.tatoeba.org/exports/per_language/cmn/cmn_sentences_detailed.tsv.bz2",
)

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun readLock(root: File): Map<String, String> =
    File(root, "data/sources.lock").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line ->
            val (hash, name) = line.trim().split(Regex("\\s+"), limit = 2)
            name to hash
        }

fun verifyLock(root: File) {
    val raw = File(root, "data/raw")
    val failures = readLock(root).mapNotNull { (name, expected) ->
        val f = File(raw, name)
        when {
            !f.exists() -> "$name: missing (run :data-ingest:downloadData)"
            sha256(f) != expected -> "$name: sha256 mismatch — upstream drifted or partial download; re-pin deliberately"
            else -> null
        }
    }
    check(failures.isEmpty()) { "sources.lock verification failed:\n" + failures.joinToString("\n") }
}

fun main() {
    val root = File(".").canonicalFile
    val raw = File(root, "data/raw").apply { mkdirs() }
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    for ((name, url) in PINNED_SOURCES) {
        val target = File(raw, name)
        if (target.exists()) {
            println("exists   $name")
            continue
        }
        println("download $name ← $url")
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url)).build(),
            HttpResponse.BodyHandlers.ofFile(target.toPath()),
        )
        check(response.statusCode() == 200) { "$name: HTTP ${response.statusCode()}" }
    }
    // Decompress the archives the pipeline reads directly.
    ProcessBuilder("gunzip", "-kf", "cedict.txt.gz").directory(raw).inheritIO().start().waitFor()
    ProcessBuilder("bunzip2", "-kf", "cmn_sentences_detailed.tsv.bz2").directory(raw).inheritIO().start().waitFor()
    ProcessBuilder("unzip", "-o", "-q", "Unihan.zip", "Unihan_IRGSources.txt", "Unihan_Readings.txt")
        .directory(raw).inheritIO().start().waitFor()
    verifyLock(root)
    println("sources.lock verified ✓")
}
