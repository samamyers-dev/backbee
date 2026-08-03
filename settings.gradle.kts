pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "backbee"

// :core and :phase0 are plain JVM modules. They build and test anywhere a JDK
// exists, which is deliberate: the logic that is expensive to get wrong (feed
// parsing, archive paging, smart resume, download planning, storage eviction)
// lives there and is covered by ordinary JUnit tests.
include(":core")
include(":phase0")

// :app needs the Android SDK. Including it unconditionally would make even
// `gradle :core:test` fail to configure on a machine without one, so we gate it.
// Android Studio always provides local.properties, so this is a no-op there.
val androidSdkAvailable: Boolean =
    sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        .filterNotNull()
        .any { File(it).isDirectory } ||
        file("local.properties").takeIf { it.isFile }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }
            ?.removePrefix("sdk.dir=")
            ?.let { File(it.trim()).isDirectory } == true

if (androidSdkAvailable) {
    include(":app")
} else {
    gradle.rootProject {
        logger.lifecycle(
            "No Android SDK found (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties) - " +
                "skipping :app. JVM modules :core and :phase0 still build."
        )
    }
}
