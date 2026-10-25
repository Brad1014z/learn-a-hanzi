package io.github.brad1014z.hanzi.ingest

import io.github.brad1014z.hanzi.engine.data.LessonContentValidationException
import io.github.brad1014z.hanzi.engine.data.LessonManifest
import io.github.brad1014z.hanzi.engine.data.requireValidLessonManifest
import java.io.File
import kotlin.system.exitProcess

/**
 * Validates the checked-in teacher manifest against the bundled dataset and audio.
 *
 * This is the deep half of the release content gate (docs/milestones/m4.1-release-gates.md).
 * It deliberately does **not** need `data/raw/`, so it can run in CI on every push and at
 * signing time:
 *
 *   ./gradlew :data-ingest:verifyLessonManifest          # CI: empty manifest is pending, not a failure
 *   ./gradlew :data-ingest:verifyPilotLessonManifest     # signing: empty manifest is a failure
 *
 * A non-empty manifest is always held to every rule, including complete teacher-approval
 * metadata. The old signing check counted `"approved": true` with a regex; that could be
 * satisfied by a manifest whose reviewer/date were blank or whose readings/audio were
 * wrong. This is the check that makes the gate real.
 */
fun main(args: Array<String>) {
    val options = parseVerifyOptions(args)
    val root = File(".").canonicalFile
    val manifestFile = resolveFromRoot(root, options.manifest)
    val databaseFile = resolveFromRoot(root, options.database)
    val assetsDir = resolveFromRoot(root, options.assets)

    check(manifestFile.isFile) { "Missing lesson manifest: ${manifestFile.path}" }
    val manifest = json.decodeFromString<LessonManifest>(manifestFile.readText())

    if (manifest.lessons.isEmpty()) {
        if (!options.allowPending) {
            fail(
                """
                No teacher-approved lesson content — refusing to build a signed release APK.

                ${manifestFile.relativeTo(root).path} approves 0 lessons, and a release build
                only offers characters approved there. Signing this would hand testers an app
                with an empty Collection.

                To proceed: land the reviewed lessons (review.approved = true, with reviewer
                and reviewedAt), then run ./gradlew :data-ingest:verifyPilotLessonManifest.
                """.trimIndent(),
            )
        }
        println(
            "verifyLessonManifest: ${manifestFile.relativeTo(root).path} is pending " +
                "(0 lessons) — deep validation skipped.",
        )
        return
    }

    val source = loadManifestSource(databaseFile)
    try {
        requireValidLessonManifest(
            manifest = manifest,
            dictionaryReadings = source.readings,
            audioExists = { assetPath -> File(assetsDir, assetPath).isFile },
            requiredCount = options.requiredCount,
            requireApproval = true,
        )
    } catch (error: LessonContentValidationException) {
        fail(error.failures.joinToString(prefix = "Lesson content rejected:\n- ", separator = "\n- "))
    }

    println(
        "verifyLessonManifest: ${manifest.lessons.size} approved lesson(s) validated " +
            "against ${databaseFile.relativeTo(root).path}.",
    )
}

private data class VerifyOptions(
    val manifest: String = "app/src/main/assets/content/lesson-content-first30.json",
    val database: String = "app/src/main/assets/databases/hanzi_v1.sqlite",
    val assets: String = "app/src/main/assets",
    val requiredCount: Int = 30,
    val allowPending: Boolean = false,
)

private fun parseVerifyOptions(args: Array<String>): VerifyOptions {
    var options = VerifyOptions()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--manifest" -> options = options.copy(manifest = args[++i])
            "--database" -> options = options.copy(database = args[++i])
            "--assets" -> options = options.copy(assets = args[++i])
            "--required-count" -> options = options.copy(requiredCount = args[++i].toInt())
            "--allow-pending" -> options = options.copy(allowPending = true)
            else -> error("Unknown argument: $arg")
        }
        i++
    }
    return options
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
