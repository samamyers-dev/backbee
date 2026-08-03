# Room, Media3 and Glance all rely on reflection over generated classes.
-keep class dev.backbee.data.db.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn org.slf4j.**
