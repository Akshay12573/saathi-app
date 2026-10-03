package com.saathi.assistant.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Talks only to the Saathi backend. No LLM provider key ever travels through here. */
interface BackendApi {

    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>

    @POST("api/research")
    suspend fun research(@Body request: ResearchRequest): Response<ResearchResponse>
}
