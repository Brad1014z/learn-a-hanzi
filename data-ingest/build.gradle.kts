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
