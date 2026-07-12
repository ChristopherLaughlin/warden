# Room / Kotlin metadata is handled by AGP defaults.
# Keep VpnService + AccessibilityService entry points referenced only from the manifest.
-keep class com.warden.blocker.vpn.** { *; }
-keep class com.warden.blocker.accessibility.** { *; }
