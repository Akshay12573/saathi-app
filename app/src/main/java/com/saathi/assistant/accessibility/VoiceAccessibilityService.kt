package com.saathi.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.assistant.agent.ScreenDumper
import com.saathi.assistant.agent.ScreenElement

/**
 * Optional, explicit user-enabled service (Settings > Accessibility > Saathi).
 * Two separate jobs, both opt-in and both only act when something else in
 * the app has explicitly armed them — this service never reacts to
 * accessibility events on its own initiative:
 *
 * 1. WhatsApp auto-send: taps WhatsApp's own "Send" button once, only when
 *    [pendingAutoSend] was set after a user-confirmed WHATSAPP_MESSAGE action.
 * 2. Cross-app agent actions (AGENT_TASK): [AgentRunner] calls [dumpCurrentScreen]
 *    and [performUiAction] directly — this service is just the hands, the
 *    backend decides what to do and MainActivity gates sensitive taps behind
 *    a confirmation dialog before calling performUiAction.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var pendingAutoSend: Boolean = false

        @Volatile
        var instance: VoiceAccessibilityService? = null
    }

    enum class UiActionKind { TAP, TYPE, SCROLL_DOWN, SCROLL_UP, BACK }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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

    /** Returns null if there's no foreground window to read right now. */
    fun dumpCurrentScreen(): List<ScreenElement>? {
        val root = rootInActiveWindow ?: return null
        return ScreenDumper.dump(root)
    }

    /** Executes one agent step against a node from the most recent [dumpCurrentScreen] call. Returns whether it actually happened. */
    fun performUiAction(kind: UiActionKind, element: ScreenElement?, textToType: String? = null): Boolean {
        return when (kind) {
            UiActionKind.TAP -> element?.info?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

            UiActionKind.TYPE -> {
                val info = element?.info ?: return false
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType ?: "")
                }
                if (!info.isFocused) info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }

            UiActionKind.SCROLL_DOWN -> element?.info?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false

            UiActionKind.SCROLL_UP -> element?.info?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false

            UiActionKind.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {
        pendingAutoSend = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }
}
