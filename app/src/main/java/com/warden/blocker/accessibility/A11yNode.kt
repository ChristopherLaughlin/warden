package com.warden.blocker.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.warden.blocker.feature.UiNode

/** Adapts an Android AccessibilityNodeInfo to the testable [UiNode] interface. */
class A11yNode(private val info: AccessibilityNodeInfo?) : UiNode {
    override val viewId: String? get() = info?.viewIdResourceName
    override val text: String? get() = info?.text?.toString()
    override val contentDesc: String? get() = info?.contentDescription?.toString()
    override val childCount: Int get() = info?.childCount ?: 0
    override fun child(index: Int): UiNode? = info?.getChild(index)?.let { A11yNode(it) }
}
