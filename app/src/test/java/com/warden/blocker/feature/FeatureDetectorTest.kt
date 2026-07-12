package com.warden.blocker.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Simple in-memory node for testing the detector. */
private class FakeNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDesc: String? = null,
    val children: List<FakeNode> = emptyList(),
) : UiNode {
    override val childCount get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
}

class FeatureDetectorTest {

    private val reels = FeatureCatalog.ALL.first { it.key == "ig_reels" }
    private val shorts = FeatureCatalog.ALL.first { it.key == "yt_shorts" }
    private val tiktok = FeatureCatalog.ALL.first { it.key == "tiktok_feed" }

    @Test fun detectsReelsByViewIdDeepInTree() {
        val tree = FakeNode(
            viewId = "com.instagram.android:id/root",
            children = listOf(
                FakeNode(viewId = "com.instagram.android:id/tab_bar"),
                FakeNode(
                    viewId = "com.instagram.android:id/coordinator",
                    children = listOf(FakeNode(viewId = "com.instagram.android:id/clips_viewer_view_pager")),
                ),
            ),
        )
        assertEquals(reels, FeatureDetector.detect(listOf(reels), tree))
    }

    @Test fun detectsTikTokByText() {
        val tree = FakeNode(children = listOf(FakeNode(contentDesc = "For You")))
        assertEquals(tiktok, FeatureDetector.detect(listOf(tiktok), tree))
    }

    @Test fun noFalsePositiveOnRegularFeed() {
        val tree = FakeNode(
            viewId = "com.instagram.android:id/feed_recycler",
            children = listOf(FakeNode(text = "Home"), FakeNode(viewId = "com.instagram.android:id/profile_tab")),
        )
        assertNull(FeatureDetector.detect(listOf(reels, shorts), tree))
    }

    @Test fun respectsMaxNodesBudget() {
        // A long chain where the match is beyond the budget must not be found.
        var node = FakeNode(viewId = "com.instagram.android:id/clips_viewer")
        repeat(50) { node = FakeNode(viewId = "filler", children = listOf(node)) }
        assertNull(FeatureDetector.detect(listOf(reels), node, maxNodes = 10))
    }
}
