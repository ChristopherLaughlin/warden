# Room / Kotlin metadata is handled by AGP defaults.
# Keep VpnService + AccessibilityService entry points referenced only from the manifest.
-keep class com.warden.blocker.vpn.** { *; }
-keep class com.warden.blocker.accessibility.** { *; }

# Strip all logging from release builds (defense against accidental sensitive logging).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
