# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keep class com.telegrambackup.network.** { *; }

# Keep Room enums (FileType, UploadStatus) - critical for Room type converters
-keep enum com.telegrambackup.data.local.entity.** { *; }

# Keep DataStore preferences
-keep class androidx.datastore.** { *; }

# Media3
-keep class androidx.media3.** { *; }
