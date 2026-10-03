package com.saathi.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional, explicit user-enabled service (Settings > Accessibility > Saathi).
 * Scoped to com.whatsapp only via accessibility_service_config.xml, so it
 * cannot see or act on any other app.
 *
 * MainActivity/ActionExecutor sets [pendingAutoSend] to true only after the
 * user has both (a) enabled this service AND (b) confirmed the specific
 * message in a dialog. This service's only job is to tap WhatsApp's own
 * "Send" button once, then immediately disarm itself.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var pendingAutoSend: Boolean = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!pendingAutoSend) return
        if (event?.packageName != "com.whatsapp") return

        val root = rootInActiveWindow ?: return
        val sendButton = findSendButton(root)
        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pendingAutoSend = false // one tap only, never repeat automatically
        }
    }

    /** WhatsApp's compose screen send button; id first, content-description as fallback. */
    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (nodes.isNotEmpty()) return nodes[0]

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.contentDescription?.toString()?.contains("Send", ignoreCase = true) == true) {
                return child
            }
            val found = findSendButton(child)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {
        pendingAutoSend = false
    }
}
