import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release identity. Play refuses an upload whose versionCode is not strictly
// greater than the last one it saw, so the number must come from somewhere
// that only ever counts up: the release workflow passes -PversionCode (see
// .github/workflows/release.yml and docs/RELEASE.md). versionName comes from
// the git tag the same way. A local build without either is "1 / 1.0.0-dev",
// which is fine for a debug install and impossible to upload by accident.
val releaseVersionCode: Int = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val releaseVersionName: String =
    (findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0-dev"

// The upload keystore is never in the repository. The release workflow decodes
// it from a secret into a temporary file and hands over the path and passwords
// through the environment; a developer machine can do the same through
// ~/.gradle/gradle.properties. When neither is present the release build type
// still assembles - unsigned - so CI can prove R8 and resource shrinking work
// on every push without holding the key.
fun secret(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: (findProperty(property) as String?)?.takeIf { it.isNotBlank() }

val uploadKeystore: File? = secret("BACKBEE_KEYSTORE_PATH", "backbeeKeystorePath")
    ?.let(::file)
    ?.takeIf { it.isFile }

android {
    namespace = "dev.backbee"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.backbee"
        minSdk = 26
        // Google Play: new apps and updates must target API 36 from 31 Aug 2026.
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optional Podcast Index credentials. Put them in ~/.gradle/gradle.properties
        // (podcastIndexKey / podcastIndexSecret) rather than in the repository; the
        // release workflow passes them in through the environment, where they do
        // not show up in the process list or in a --stacktrace dump. Without them
        // the app works from feeds alone; with them, a truncated archive can be
        // completed from the directory.
        buildConfigField("String", "PODCAST_INDEX_KEY", "\"${secret("PODCAST_INDEX_KEY", "podcastIndexKey") ?: ""}\"")
        buildConfigField("String", "PODCAST_INDEX_SECRET", "\"${secret("PODCAST_INDEX_SECRET", "podcastIndexSecret") ?: ""}\"")

        // Linked from Settings and from the Play listing. See docs/PLAY_STORE.md
        // for hosting; change both places together.
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://samamyers-dev.github.io/backbee/PRIVACY\"")
    }

    signingConfigs {
        create("release") {
            if (uploadKeystore != null) {
                storeFile = uploadKeystore
                storePassword = secret("BACKBEE_KEYSTORE_PASSWORD", "backbeeKeystorePassword")
                keyAlias = secret("BACKBEE_KEY_ALIAS", "backbeeKeyAlias") ?: "upload"
                keyPassword = secret("BACKBEE_KEY_PASSWORD", "backbeeKeyPassword")
                    ?: secret("BACKBEE_KEYSTORE_PASSWORD", "backbeeKeystorePassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null when no keystore is configured: the artifact is then unsigned,
            // which Gradle reports plainly rather than falling back to the debug
            // key and producing something that looks shippable but is not.
            signingConfig = if (uploadKeystore != null) signingConfigs.getByName("release") else null
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

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        // lintVital runs inside every release build and fails it on fatal
        // issues; that is the gate. The full lint pass is advisory - CI runs
        // it and publishes the report rather than blocking on style findings.
        abortOnError = false
        checkReleaseBuilds = true
        htmlReport = true
        textReport = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Roborazzi writes rather than compares. There is no committed
                // baseline to diff against - the point here is to look at the
                // output, not to gate on pixel equality.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            // Most of Media3 that a real player touches - ExoPlayer itself,
            // MediaLibraryService, the data-source factories - is annotated
            // @UnstableApi, which is a RequiresOptIn at ERROR level. Opting in
            // once here beats annotating every playback class.
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

ksp {
    // Emits the schema JSON that Room needs for migration tests and for
    // reviewing what a schema change actually did.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.ext.junit)

    // Screenshot rendering on the JVM: Robolectric's native graphics mode draws
    // real Compose output to PNG without an emulator or a system image.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}
