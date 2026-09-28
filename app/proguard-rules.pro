# Reglas ProGuard/R8 — IPTV Player Pro
# La compilación release se entrega sin minificación por defecto para máxima
# compatibilidad con las librerías de terceros (WireGuard, Media3, Retrofit).

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.iptvplayerpro.**$$serializer { *; }
-keepclassmembers class com.iptvplayerpro.** { *** Companion; }
-keepclasseswithmembers class com.iptvplayerpro.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# WireGuard tunnel
-keep class com.wireguard.** { *; }
