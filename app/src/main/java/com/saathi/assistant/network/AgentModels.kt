package com.saathi.assistant.network

import com.google.gson.annotations.SerializedName

/** Wire format shared with the backend's POST /api/agent-step (see backend/src/routes/agentStep.js). */

data class ScreenNodeDto(
    val index: Int,
    val role: String,
    val text: String,
    @SerializedName("content_desc") val contentDesc: String,
    @SerializedName("resource_id") val resourceId: String,
    val clickable: Boolean,
    val editable: Boolean
)

data class StepHistoryEntryDto(
    val action: String,
    @SerializedName("target_text") val targetText: String? = null,
    @SerializedName("text_to_type") val textToType: String? = null
)

data class AgentStepRequest(
    val goal: String,
    @SerializedName("screen_dump") val screenDump: List<ScreenNodeDto>,
    @SerializedName("step_history") val stepHistory: List<StepHistoryEntryDto>
)

data class AgentStepResponse(
    val action: String,
    @SerializedName("target_index") val targetIndex: Int?,
    @SerializedName("text_to_type") val textToType: String?,
    @SerializedName("is_sensitive") val isSensitive: Boolean,
    val message: String
)
