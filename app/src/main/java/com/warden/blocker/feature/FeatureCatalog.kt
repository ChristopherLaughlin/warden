package com.warden.blocker.feature

/**
 * A blockable slice of a specific app (e.g. Instagram Reels), identified on-screen by
 * accessibility signals: resource-id fragments and/or visible text / content-descriptions.
 *
 * NOTE: these signals are inherently version-sensitive — app vendors rename view ids and
 * restructure UIs between releases. This is true of every app in this category (ScreenZen
 * et al. ship signal updates too). Keep [viewIdContains] as the primary signal and
 * [textContains] as a looser fallback; both are matched case-insensitively as substrings.
 */
data class AppFeature(
    val key: String,
    val packageName: String,
    val label: String,
    val description: String,
    val viewIdContains: List<String> = emptyList(),
    val textContains: List<String> = emptyList(),
)

object FeatureCatalog {
    val ALL: List<AppFeature> = listOf(
        AppFeature(
            key = "yt_shorts",
            packageName = "com.google.android.youtube",
            label = "YouTube Shorts",
            description = "The vertical Shorts feed",
            viewIdContains = listOf("reel_recycler", "reel_watch_fragment", "shorts_"),
            textContains = listOf(),
        ),
        AppFeature(
            key = "ig_reels",
            packageName = "com.instagram.android",
            label = "Instagram Reels",
            description = "The full-screen Reels feed",
            viewIdContains = listOf("clips_viewer", "clips_tab", "reel_viewer"),
            textContains = listOf(),
        ),
        AppFeature(
            key = "ig_explore",
            packageName = "com.instagram.android",
            label = "Instagram Explore",
            description = "The Explore / search grid",
            viewIdContains = listOf("explore_grid", "explore_recycler"),
            textContains = listOf(),
        ),
        AppFeature(
            key = "fb_reels",
            packageName = "com.facebook.katana",
            label = "Facebook Reels",
            description = "The Reels feed in Facebook",
            viewIdContains = listOf("reels_", "reel_viewer"),
            textContains = listOf(),
        ),
        AppFeature(
            key = "tiktok_feed",
            packageName = "com.zhiliaoapp.musically",
            label = "TikTok For You",
            description = "The For You feed",
            viewIdContains = listOf("feed_recycler", "for_you"),
            textContains = listOf("For You"),
        ),
        AppFeature(
            key = "snap_spotlight",
            packageName = "com.snapchat.android",
            label = "Snapchat Spotlight",
            description = "The Spotlight feed",
            viewIdContains = listOf("spotlight"),
            textContains = listOf(),
        ),
    )

    fun byPackage(packageName: String): List<AppFeature> =
        ALL.filter { it.packageName == packageName }
}
