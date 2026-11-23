# Keep source file names and line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep app model classes (data classes used with JSON parsing)
-keep class com.soul.neurokaraoke.data.model.** { *; }

# Media3 / ExoPlayer (consumer rules bundled in AARs handle required keeps)
-dontwarn androidx.media3.**

# Coil image loading (consumer rules bundled in AARs)
-dontwarn coil.**

# Jetpack Compose (consumer rules bundled in AARs)
-dontwarn androidx.compose.**

# Android Car App Library (consumer rules bundled in AARs)
-dontwarn androidx.car.app.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Kotlin serialization / reflection
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# OkHttp / network
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep enum classes intact
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
