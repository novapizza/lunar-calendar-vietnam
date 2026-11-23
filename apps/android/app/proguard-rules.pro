# kotlinx.serialization: keep generated serializers of the shared-data models.
-keepclassmembers class io.github.novapizza.licham.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep class io.github.novapizza.licham.core.**$$serializer { *; }

# WorkManager 2.7.1 (pulled in by Glance) opens its Room 2.2.5 database by reflection, looking up
# WorkDatabase_Impl by name and calling its no-argument constructor: R8 renamed it, and the release
# build crashed at start (2026-11-23, "Failed to create an instance of androidx.work.impl.WorkDatabase").
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# WorkManager 2.7.1 also creates its InputMerger by reflection (no-argument constructor) and its workers
# (Glance's session worker) by their (Context, WorkerParameters) constructor. R8 removed the merger's
# constructor and widgets never left their loading state on real phones (Xiaomi HyperOS 4, Samsung One UI 9;
# reproduced on the emulator, 2026-11-23: "OverwritingInputMerger has no zero argument constructor").
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
