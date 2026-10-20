# Proguard & R8 Rules for Mousikify Music Player

# -------------------------------------------------------------
# 1. JAudioTagger (Reflection-based audio format detection)
# -------------------------------------------------------------
-keep class net.jthink.jaudiotagger.** { *; }
-dontwarn net.jthink.jaudiotagger.**

# -------------------------------------------------------------
# 2. AndroidX Media3 / ExoPlayer & Audio Codecs
# -------------------------------------------------------------
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.decoder.** { *; }
-dontwarn androidx.media3.**

# Keep custom AudioProcessors and ForwardingPlayer implementations
-keep class com.mousikify.core.media.dsp.** { *; }
-keep class com.mousikify.core.media.player.** { *; }

# -------------------------------------------------------------
# 3. Room Database & SQLite
# -------------------------------------------------------------
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class com.mousikify.core.database.entity.** { *; }
-keep class com.mousikify.core.database.dao.** { *; }

# -------------------------------------------------------------
# 4. Domain & Model Classes (Data entities and summaries)
# -------------------------------------------------------------
-keep class com.mousikify.core.model.** { *; }
-keepclassmembers class com.mousikify.core.model.** { *; }

# -------------------------------------------------------------
# 5. WorkManager & Hilt Workers
# -------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.mousikify.core.scanner.work.** { *; }

# -------------------------------------------------------------
# 6. Coroutines & DataStore
# -------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# -------------------------------------------------------------
# 7. Logging Sanitization (Strip debug & info logs in release)
# -------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
