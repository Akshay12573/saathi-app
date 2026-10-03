package com.saathi.assistant.network

/**
 * Wire format shared with the backend's POST /api/chat.
 * Keep this in sync with backend/src/llm/promptSchema.js — that file defines
 * the JSON shape the LLM is instructed to return, this just deserializes it.
 */
data class ChatRequest(
    val session_id: String,
    val text: String
)

data class ActionDto(
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val requires_confirmation: Boolean = false,
    val confirmation_prompt: String? = null
)

data class ChatResponse(
    val reply_text: String,
    val actions: List<ActionDto> = emptyList(),
    val needs_more_info: Boolean = false,
    val follow_up_question: String? = null
)

data class ResearchRequest(
    val query: String
)

data class ResearchResponse(
    val summary: String,
    val sources: List<String> = emptyList()
)
