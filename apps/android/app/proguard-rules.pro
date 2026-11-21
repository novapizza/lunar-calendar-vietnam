# kotlinx.serialization: keep generated serializers of the shared-data models.
-keepclassmembers class io.github.novapizza.licham.core.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep class io.github.novapizza.licham.core.**$$serializer { *; }
