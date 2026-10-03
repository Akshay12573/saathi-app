package com.saathi.assistant.agent

import android.view.accessibility.AccessibilityNodeInfo

/** One screen element paired with the live node it came from, so an action can be performed on it immediately after. */
data class ScreenElement(val node: UiNode, val info: AccessibilityNodeInfo)

/**
 * Flattens the current window's accessibility tree into a compact list the
 * backend can reason about. Capped at [maxNodes] so the payload stays small
 * and the LLM isn't drowning in noise — clickable/editable elements are
 * prioritized over plain text.
 */
object ScreenDumper {

    fun dump(root: AccessibilityNodeInfo, maxNodes: Int = 60): List<ScreenElement> {
        val interactive = mutableListOf<ScreenElement>()
        val textOnly = mutableListOf<ScreenElement>()
        var nextIndex = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40) return // guard against pathological trees

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val isInteractive = node.isClickable || node.isEditable || node.isCheckable || node.isScrollable

            if (isInteractive || text.isNotEmpty() || desc.isNotEmpty()) {
                val element = ScreenElement(
                    node = UiNode(
                        index = nextIndex++,
                        role = classifyRole(node),
                        text = text,
                        contentDesc = desc,
                        resourceId = node.viewIdResourceName.orEmpty(),
                        clickable = node.isClickable,
                        editable = node.isEditable
                    ),
                    info = node
                )
                if (isInteractive) interactive.add(element) else textOnly.add(element)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child, depth + 1)
            }
        }

        visit(root, 0)

        val combined = (interactive + textOnly).take(maxNodes)
        // Re-index sequentially after truncation so indices sent to the backend are contiguous.
        return combined.mapIndexed { i, el -> el.copy(node = el.node.copy(index = i)) }
    }

    private fun classifyRole(node: AccessibilityNodeInfo): String {
        val className = node.className?.toString().orEmpty()
        return when {
            node.isEditable -> "edit"
            className.contains("Button", ignoreCase = true) -> "button"
            className.contains("EditText", ignoreCase = true) -> "edit"
            className.contains("ImageView", ignoreCase = true) -> "image"
            className.contains("TextView", ignoreCase = true) -> "text"
            node.isScrollable -> "scrollable"
            node.isClickable -> "button"
            else -> "other"
        }
    }
}
