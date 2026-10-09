# Room, Hilt, WorkManager and Compose ship their own consumer rules.
# Built-in templates are loaded as Java resources from the domain jar; keep them packaged.
-keepclassmembers class app.margin.domain.template.BuiltInTemplates { *; }
# kotlinx.serialization is used only through the JsonElement tree API (no reflection, no generated serializers).
-dontwarn kotlinx.serialization.**
# ML Kit text recognition
-dontwarn com.google.mlkit.**
