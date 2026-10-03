package com.saathi.assistant.agent

/** Compact, serializable view of one on-screen element for the backend agent-step planner. */
data class UiNode(
    val index: Int,
    val role: String,
    val text: String,
    val contentDesc: String,
    val resourceId: String,
    val clickable: Boolean,
    val editable: Boolean
)
