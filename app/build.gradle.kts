import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization) // @Serializable payloads (Outbox, profiles)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The cloud layer lights up only when the Firebase config exists (docs/specs/12):
// without app/google-services.json the app builds fine and runs the offline fakes —
// CI and fresh checkouts never need a Firebase account.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Release signing (docs/adr/0001, spec 01): the keystore never enters the repo. CI
// decodes KEYSTORE_BASE64 to a file and passes these four env vars; a developer machine
// can do the same. Without them the release build is simply unsigned (still useful for
// checking R8) — never debug-signed, which would be indistinguishable from a real build.
val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
val releaseSigningReady = releaseKeystore != null &&
    !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty() &&
    !System.getenv("KEY_ALIAS").isNullOrEmpty() &&
    !System.getenv("KEY_PASSWORD").isNullOrEmpty()

android {
    // FINAL applicationId (docs/adr/0001-application-id-frozen.md, 2026-09-04): an owned
    // io.github.<user> namespace, frozen before the first pilot install so testers never
    // have to uninstall and lose their progress. The display name is a resource
    // (@string/app_name), so naming stays the co-designer's call (spec 11).
    namespace = "io.github.brad1014z.hanzi"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.brad1014z.hanzi"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-pilot"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // M4.1 pilot freeze: cloud, sign-in, Family, sync, and boards stay unreachable.
        buildConfigField("boolean", "SOCIAL_ENABLED", "false")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric harness for Room DAOs + migrations (spec 03/08).
            isIncludeAndroidResources = true
        }
    }

}

// `android.kotlinOptions` is deprecated in the Kotlin 2.x Gradle plugin; the JVM target
// lives in the Kotlin DSL instead (same value, no build warning).
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    // Schema JSON checked in from v1 so migrations are testable (spec 03); the Room
    // plugin also feeds these to MigrationTestHelper as test assets.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    // Optional cloud layer (spec 12/M4): compiled in, inert without google-services.json.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
