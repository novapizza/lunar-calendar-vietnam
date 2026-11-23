# kotlinx.serialization: keep generated serializers of the shared-data models.
-keepclassmembers class io.github.novapizza.licham.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep class io.github.novapizza.licham.core.**$$serializer { *; }

# WorkManager 2.7.1 (pulled in by Glance) opens its Room 2.2.5 database by reflection, looking up
# WorkDatabase_Impl by name and calling its no-argument constructor: R8 renamed it, and the release
# build crashed at start (2026-11-23, "Failed to create an instance of androidx.work.impl.WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
