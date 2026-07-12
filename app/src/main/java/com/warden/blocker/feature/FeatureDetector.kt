package com.warden.blocker.feature

/**
 * Minimal, platform-agnostic view of an accessibility node so the detection logic can be
 * unit-tested without an Android AccessibilityNodeInfo.
 */
interface UiNode {
    val viewId: String?
    val text: String?
    val contentDesc: String?
    val childCount: Int
    fun child(index: Int): UiNode?
}

/**
 * Scans a node tree for any of the given [features]. Bounded by [maxNodes] so a deep tree
 * (e.g. a long feed) can't stall the accessibility thread. Returns the first feature whose
 * signals appear on screen, or null.
 */
object FeatureDetector {

    fun detect(features: List<AppFeature>, root: UiNode?, maxNodes: Int = 500): AppFeature? {
        if (root == null || features.isEmpty()) return null
        val stack = ArrayDeque<UiNode>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++
            features.firstOrNull { nodeMatches(it, node) }?.let { return it }
            for (i in 0 until node.childCount) node.child(i)?.let { stack.addLast(it) }
        }
        return null
    }

    fun nodeMatches(feature: AppFeature, node: UiNode): Boolean {
        val vid = node.viewId
        if (vid != null && feature.viewIdContains.any { vid.contains(it, ignoreCase = true) }) return true
        if (feature.textContains.isNotEmpty()) {
            val haystack = "${node.text.orEmpty()}${node.contentDesc.orEmpty()}"
            if (feature.textContains.any { it.isNotEmpty() && haystack.contains(it, ignoreCase = true) }) return true
        }
        return false
    }
}
