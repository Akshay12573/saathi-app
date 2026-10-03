package com.saathi.assistant.agent

import com.saathi.assistant.accessibility.VoiceAccessibilityService
import com.saathi.assistant.network.AgentStepRequest
import com.saathi.assistant.network.RetrofitClient
import com.saathi.assistant.network.ScreenNodeDto
import com.saathi.assistant.network.StepHistoryEntryDto
import kotlinx.coroutines.delay

/**
 * Drives the "open an app and do something inside it" flow: dump the current
 * screen, ask the backend for exactly one next step, execute it (pausing for
 * explicit confirmation on sensitive taps), repeat. Capped at [MAX_STEPS] so
 * a confused loop can't run forever.
 */
class AgentRunner {

    interface Listener {
        fun onLog(line: String)
        suspend fun confirmSensitiveStep(description: String): Boolean
        fun onFinished(success: Boolean, message: String)
    }

    companion object {
        private const val MAX_STEPS = 15
        private const val STEP_SETTLE_DELAY_MS = 600L
        private const val FIRST_STEP_SETTLE_DELAY_MS = 1500L // cold app launches are slow
        private const val EMPTY_SCREEN_RETRY_DELAY_MS = 900L
        private const val MAX_EMPTY_SCREEN_RETRIES = 2
        private const val MAX_IDENTICAL_REPEATS = 3 // same action+target this many times in a row = stuck
    }

    suspend fun run(goal: String, appName: String?, listener: Listener) {
        val service = VoiceAccessibilityService.instance
        if (service == null) {
            listener.onFinished(
                false,
                "Accessibility Service on nahi hai — Settings > Accessibility > Saathi mein enable karo, phir dobara try karo."
            )
            return
        }

        val history = mutableListOf<StepHistoryEntryDto>()
        var lastActionKey: String? = null
        var identicalRepeatCount = 0

        for (step in 1..MAX_STEPS) {
            delay(if (step == 1) FIRST_STEP_SETTLE_DELAY_MS else STEP_SETTLE_DELAY_MS)

            val firstDump = service.dumpCurrentScreen()
            if (firstDump == null) {
                listener.onFinished(false, "Screen padh nahi payi — koi app foreground mein nahi hai shayad.")
                return
            }

            // Cold app launches sometimes still show a blank/splash screen right
            // after open — retry a couple of times locally (no API call spent)
            // before asking the backend to plan against an empty dump.
            var elements: List<ScreenElement> = firstDump
            var emptyRetries = 0
            while (elements.isEmpty() && emptyRetries < MAX_EMPTY_SCREEN_RETRIES) {
                delay(EMPTY_SCREEN_RETRY_DELAY_MS)
                elements = service.dumpCurrentScreen() ?: emptyList()
                emptyRetries++
            }

            val dumpDto = elements.map {
                ScreenNodeDto(
                    index = it.node.index,
                    role = it.node.role,
                    text = it.node.text,
                    contentDesc = it.node.contentDesc,
                    resourceId = it.node.resourceId,
                    clickable = it.node.clickable,
                    editable = it.node.editable
                )
            }

            val response = try {
                val resp = RetrofitClient.api.agentStep(AgentStepRequest(goal, dumpDto, history, appName))
                if (!resp.isSuccessful) {
                    listener.onFinished(false, "Agent backend error (HTTP ${resp.code()})")
                    return
                }
                resp.body() ?: run {
                    listener.onFinished(false, "Agent backend se khali response mila.")
                    return
                }
            } catch (e: Exception) {
                listener.onFinished(false, "Agent step fail ho gaya: ${e.message}")
                return
            }

            // Stuck-loop guard: same action on the same element 3 times running,
            // without the LLM itself noticing, means nothing is actually changing.
            if (response.action == "TAP" || response.action == "TYPE") {
                val actionKey = "${response.action}:${response.targetIndex}"
                if (actionKey == lastActionKey) {
                    identicalRepeatCount++
                } else {
                    identicalRepeatCount = 1
                    lastActionKey = actionKey
                }
                if (identicalRepeatCount >= MAX_IDENTICAL_REPEATS) {
                    listener.onFinished(false, "Same cheez baar-baar try ho rahi thi, lag raha hai atak gaya — ruk rahi hoon.")
                    return
                }
            } else {
                lastActionKey = null
                identicalRepeatCount = 0
            }

            when (response.action) {
                "DONE" -> {
                    listener.onFinished(true, response.message.ifBlank { "Ho gaya." })
                    return
                }

                "FAILED" -> {
                    listener.onFinished(false, response.message.ifBlank { "Ye kaam nahi ho paya." })
                    return
                }

                "BACK" -> {
                    service.performUiAction(VoiceAccessibilityService.UiActionKind.BACK, null)
                    history.add(StepHistoryEntryDto("BACK"))
                    listener.onLog("Saathi: back ja raha hoon")
                }

                "SCROLL_DOWN", "SCROLL_UP" -> {
                    val target = elements.find { it.node.index == response.targetIndex }
                    val kind = if (response.action == "SCROLL_DOWN") {
                        VoiceAccessibilityService.UiActionKind.SCROLL_DOWN
                    } else {
                        VoiceAccessibilityService.UiActionKind.SCROLL_UP
                    }
                    service.performUiAction(kind, target)
                    history.add(StepHistoryEntryDto(response.action, targetLabel(target)))
                    listener.onLog("Saathi: scroll kar raha hoon")
                }

                "TAP" -> {
                    val target = elements.find { it.node.index == response.targetIndex }
                    if (target == null) {
                        listener.onFinished(false, "Jis cheez pe tap karna tha wo screen pe nahi mili.")
                        return
                    }
                    if (response.isSensitive) {
                        val label = targetLabel(target) ?: "ye button"
                        val proceed = listener.confirmSensitiveStep("\"$label\" dabau?")
                        if (!proceed) {
                            listener.onFinished(false, "Cancel kar diya aapne.")
                            return
                        }
                    }
                    service.performUiAction(VoiceAccessibilityService.UiActionKind.TAP, target)
                    history.add(StepHistoryEntryDto("TAP", targetLabel(target)))
                    listener.onLog("Saathi: tap kiya${targetLabel(target)?.let { " ($it)" } ?: ""}")
                }

                "TYPE" -> {
                    val target = elements.find { it.node.index == response.targetIndex }
                    if (target == null) {
                        listener.onFinished(false, "Jis field mein type karna tha wo screen pe nahi mili.")
                        return
                    }
                    service.performUiAction(VoiceAccessibilityService.UiActionKind.TYPE, target, response.textToType)
                    history.add(StepHistoryEntryDto("TYPE", targetLabel(target), response.textToType))
                    listener.onLog("Saathi: type kar raha hoon")
                }

                else -> {
                    listener.onFinished(false, "Agent ne anjaan action bola: ${response.action}")
                    return
                }
            }
        }

        listener.onFinished(false, "$MAX_STEPS steps tak pahunch gaya bina goal poora kiye, ruk rahi hoon.")
    }

    private fun targetLabel(target: ScreenElement?): String? {
        if (target == null) return null
        return target.node.text.ifBlank { target.node.contentDesc }.ifBlank { null }
    }
}
