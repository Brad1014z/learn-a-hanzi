# Release keep rules (spec 01: "R8/ProGuard with keep rules for Room and any reflection").
# Room, Firebase, WorkManager and Compose ship their own consumer rules; the two things
# this app adds on top are kotlinx.serialization payloads and the engine's data classes.

# --- kotlinx.serialization -------------------------------------------------------
# Serializers are looked up reflectively via the generated Companion / $serializer.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.brad1014z.hanzi.**$$serializer { *; }
-keepclassmembers class io.github.brad1014z.hanzi.** { *** Companion; }
-keepclasseswithmembers class io.github.brad1014z.hanzi.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Sync payloads + Firestore document mapping ----------------------------------
# The outbox payloads round-trip through JSON and the cloud layer maps Firestore docs
# to/from these classes by field name; keep their shape intact.
-keep class io.github.brad1014z.hanzi.data.Outbox$* { *; }
-keep class io.github.brad1014z.hanzi.engine.social.** { *; }

# --- Coroutines / debugging niceties -----------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Robolectric-only test hooks and the JVM sqlite driver never reach the APK.
-dontwarn org.xerial.**
-dontwarn org.robolectric.**
