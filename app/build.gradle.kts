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

android {
    // Prototype namespace under the maintainer's GitHub Pages domain (docs/specs/01:
    // final applicationId is decided before the first Play upload, not now).
    namespace = "io.github.brad1014z.hanzi"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.brad1014z.hanzi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric harness for Room DAOs + migrations (spec 03/08).
            isIncludeAndroidResources = true
        }
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
