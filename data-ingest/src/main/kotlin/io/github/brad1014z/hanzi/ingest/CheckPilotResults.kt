package io.github.brad1014z.hanzi.ingest

/**
 * `./gradlew :data-ingest:checkPilotResults` entry point. Separate main class so Gradle's
 * `--args` cannot silently drop the `--require-gate` flag.
 */
fun main(args: Array<String>) = pilotMain(args + "--require-gate")
