# Room, Media3 and Glance all rely on reflection over generated classes.
-keep class dev.backbee.data.db.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn org.slf4j.**

# Verbose and debug logging is compiled out of the release build. Info,
# warning and error stay: the settings readout ("DB CHECKPOINT", "FEED
# REFRESH") and a bug report both depend on them.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Keep the line numbers so a stack trace from Play Console can be read back
# through the mapping file, without keeping the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
