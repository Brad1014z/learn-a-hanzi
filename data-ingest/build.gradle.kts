// The ingestion tool (docs/specs/02): a plain JVM module, NOT shipped in the APK.
// `./gradlew :data-ingest:run` regenerates the bundled dataset deterministically from
// data/raw/ + data/pinned/; `:data-ingest:downloadData` (re)fetches data/raw/ and
// verifies it against data/sources.lock.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":engine")) // SVG parser + coordinate normalization (spec 06 reuse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

tasks.register<JavaExec>("downloadData") {
    group = "ingest"
    description = "Download pinned raw sources into data/raw/ and verify data/sources.lock"
    mainClass.set("io.github.brad1014z.hanzi.ingest.DownloadKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

tasks.register<JavaExec>("run") {
    group = "ingest"
    description = "Build app/src/main/assets/databases/hanzi_v1.sqlite from data/"
    mainClass.set("io.github.brad1014z.hanzi.ingest.IngestKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

tasks.register<JavaExec>("generateAudio") {
    group = "ingest"
    description = "Generate TTS clips for the dataset (needs GOOGLE_TTS_API_KEY; deliberate, billable)"
    mainClass.set("io.github.brad1014z.hanzi.ingest.GenerateAudioKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

// The deep half of the M4.1 content gate (docs/milestones/m4.1-release-gates.md): the
// checked-in manifest is validated against the bundled dataset and audio. It needs no
// data/raw/, so CI can run it on every push. An empty manifest is "pending" for CI and a
// hard failure for the signing task.
tasks.register<JavaExec>("verifyLessonManifest") {
    group = "verification"
    description = "Validate the checked-in teacher manifest (empty = pending, not a failure)"
    mainClass.set("io.github.brad1014z.hanzi.ingest.VerifyLessonManifestKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    args("--allow-pending")
}

tasks.register<JavaExec>("verifyPilotLessonManifest") {
    group = "verification"
    description = "Strict manifest gate for a signed release: empty or invalid fails"
    mainClass.set("io.github.brad1014z.hanzi.ingest.VerifyLessonManifestKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

// Teacher-review workflow: never approves anything, never touches the app asset.
tasks.register<JavaExec>("draftLessonContent") {
    group = "ingest"
    description = "Generate a first-30 teacher-review draft + HTML/CSV review sheet"
    mainClass.set("io.github.brad1014z.hanzi.ingest.DraftLessonContentKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

// Corpus loop (docs/milestones/m4.1-release-gates.md): raw JSONL pulled from a pilot
// phone → FRR/FAR report. The raw exports stay local; only the report is committed.
tasks.register<JavaExec>("analyzePilotCorpus") {
    group = "verification"
    description = "Analyze pilot stroke JSONL and write data/corpus/corpus-report.md"
    mainClass.set("io.github.brad1014z.hanzi.ingest.CorpusAnalyzerKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

tasks.register<JavaExec>("checkPilotCorpus") {
    group = "verification"
    description = "Fail unless the corpus meets the M4.1 counts, FRR and FAR gates"
    mainClass.set("io.github.brad1014z.hanzi.ingest.CheckPilotCorpusKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
}

// Tests read the checked-in dataset/audio by repo-relative path, like the tasks above.
tasks.test {
    workingDir = rootDir
}
