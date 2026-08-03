// Intentionally empty.
//
// Plugin aliases are declared in the modules that use them rather than here with
// `apply false`. Gradle resolves a root-level `apply false` alias eagerly, which
// would drag the Android Gradle Plugin (and therefore google()) into the build
// even when :app is excluded for lack of an SDK. Keeping the root bare is what
// lets `gradle :core:test` run on a plain JDK.
