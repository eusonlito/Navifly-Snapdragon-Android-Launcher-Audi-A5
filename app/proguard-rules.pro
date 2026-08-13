# Binder interfaces are called by the manufacturer service across process
# boundaries. Keep their names and method signatures stable in production.
-keep class com.szchoiceway.eventcenter.** { *; }

# Services and activities are referenced by name from AndroidManifest.xml.
-keep class com.lito.a5launcher.MainActivity { *; }
-keep class com.lito.a5launcher.TelemetryService { *; }
