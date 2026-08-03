plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.backbee"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.backbee"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optional Podcast Index credentials. Put them in ~/.gradle/gradle.properties
        // (podcastIndexKey / podcastIndexSecret) rather than in the repository.
        // Without them the app works from feeds alone; with them, a truncated
        // archive can be completed from the directory.
        buildConfigField("String", "PODCAST_INDEX_KEY", "\"${properties["podcastIndexKey"] ?: ""}\"")
        buildConfigField("String", "PODCAST_INDEX_SECRET", "\"${properties["podcastIndexSecret"] ?: ""}\"")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            // Most of Media3 that a real player touches - ExoPlayer itself,
            // MediaLibraryService, the data-source factories - is annotated
            // @UnstableApi, which is a RequiresOptIn at ERROR level. Opting in
            // once here beats annotating every playback class.
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
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
