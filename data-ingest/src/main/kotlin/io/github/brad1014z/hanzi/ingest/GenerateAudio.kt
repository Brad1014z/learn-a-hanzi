package io.github.brad1014z.hanzi.ingest

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Pre-generated TTS clips (spec 01/02, M2): a deliberate, key-requiring step — run
 * once per dataset change, review the output, check the clips in. Never called at app
 * runtime; the app plays the bundled clips via PregenAudioSpeechService with device-TTS
 * fallback for anything uncovered.
 *
 *   GOOGLE_TTS_API_KEY=… ./gradlew :data-ingest:generateAudio
 *
 * Voice pinned via GOOGLE_TTS_VOICE (default cmn-CN-Wavenet-A); recorded in the manifest.
 * Note (spec 07, polyphonic characters): a bare-character clip bakes in ONE reading —
 * same limitation as device TTS; the polyphonic-display rule stays a UI concern.
 */
fun main() {
    val key = System.getenv("GOOGLE_TTS_API_KEY")
        ?: error("GOOGLE_TTS_API_KEY is not set. This step is deliberate and billable — see data-ingest/README.md")
    val voice = System.getenv("GOOGLE_TTS_VOICE") ?: "cmn-CN-Wavenet-A"

    val root = File(".").canonicalFile
    val dataset = File(root, "app/src/main/assets/databases/hanzi_v1.sqlite")
    check(dataset.exists()) { "Run :data-ingest:run first — $dataset missing" }

    val texts = DriverManager.getConnection("jdbc:sqlite:${dataset.path}").use { conn ->
        conn.createStatement().use { st ->
            buildList {
                for (q in listOf(
                    "SELECT character FROM Character",
                    "SELECT simplified FROM Word",
                    "SELECT text FROM Sentence",
                )) {
                    val rs = st.executeQuery(q)
                    while (rs.next()) add(rs.getString(1))
                }
            }
        }
    }.distinct().sorted()

    val outDir = File(root, "app/src/main/assets/audio/zh-Hans").apply { mkdirs() }
    val client = HttpClient.newHttpClient()
    val sha1 = { s: String ->
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    var generated = 0
    val manifest = LinkedHashMap<String, String>()
    for (text in texts) {
        val hash = sha1(text)
        manifest[hash] = text
        val clip = File(outDir, "$hash.mp3")
        if (clip.exists()) continue
        val body = buildJsonObject {
            putJsonObject("input") { put("text", text) }
            putJsonObject("voice") { put("languageCode", "cmn-CN"); put("name", voice) }
            putJsonObject("audioConfig") { put("audioEncoding", "MP3"); put("speakingRate", 0.85) }
        }
        val response = client.send(
            HttpRequest.newBuilder(URI.create("https://texttospeech.googleapis.com/v1/text:synthesize?key=$key"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() == 200) { "TTS failed for \"$text\": HTTP ${response.statusCode()} ${response.body().take(300)}" }
        val audio = Json.parseToJsonElement(response.body()).jsonObject["audioContent"]!!.jsonPrimitive.content
        clip.writeBytes(Base64.getDecoder().decode(audio))
        generated++
        if (generated % 50 == 0) println("…$generated clips")
    }

    // manifest.json: sha1 → text, plus provenance under "_meta".
    val manifestJson = buildString {
        appendLine("{")
        appendLine("  \"_meta\": {\"voice\": \"$voice\", \"api\": \"google-cloud-tts-v1\"},")
        append(manifest.entries.joinToString(",\n") { (h, t) -> "  \"$h\": ${Json.encodeToString(kotlinx.serialization.serializer<String>(), t)}" })
        appendLine()
        append("}")
    }
    File(outDir, "manifest.json").writeText(manifestJson)
    println("audio: ${texts.size} texts, $generated newly generated → ${outDir.relativeTo(root)}")
}
