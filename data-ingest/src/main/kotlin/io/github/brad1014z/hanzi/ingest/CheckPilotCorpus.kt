package io.github.brad1014z.hanzi.ingest

/**
 * `./gradlew :data-ingest:checkPilotCorpus` entry point. It is a separate main class so
 * Gradle's `--args` (which replaces a task's configured args) cannot silently drop the
 * `--require-gate` flag.
 */
fun main(args: Array<String>) = corpusMain(args + "--require-gate")
