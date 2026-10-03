package com.saathi.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.saathi.assistant.actions.ActionExecutor
import com.saathi.assistant.actions.ActionMapper
import com.saathi.assistant.actions.LocalIntentParser
import com.saathi.assistant.actions.MappedAction
import com.saathi.assistant.agent.AgentRunner
import com.saathi.assistant.databinding.ActivityMainBinding
import com.saathi.assistant.network.ActionDto
import com.saathi.assistant.network.ChatRequest
import com.saathi.assistant.network.ResearchRequest
import com.saathi.assistant.network.RetrofitClient
import com.saathi.assistant.session.SessionManager
import com.saathi.assistant.util.PermissionUtils
import com.saathi.assistant.voice.SpeechInputManager
import com.saathi.assistant.voice.TtsManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechInput: SpeechInputManager
    private lateinit var ttsManager: TtsManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var sessionId: String

    private val conversationLog = StringBuilder()
    private var pendingPermissionAction: MappedAction? = null

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val allGranted = results.values.all { it }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (allGranted && action != null) {
            confirmAndExecute(action)
        } else if (action != null) {
            appendLine("Saathi: Permission nahi mili, ye kaam nahi ho payega.")
        }
    }

    private val micPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else appendLine("Saathi: Mic permission chahiye bolne ke liye.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speechInput = SpeechInputManager(this)
        ttsManager = TtsManager(this)
        actionExecutor = ActionExecutor(this)
        sessionId = SessionManager.getOrCreateSessionId(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionIfNeeded()
        }

        binding.micButton.setOnClickListener { onMicPressed() }
        binding.sendButton.setOnClickListener { onSendPressed() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (PermissionUtils.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)) return
        permissionRequest.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun onMicPressed() {
        if (!PermissionUtils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
            micPermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startListening()
    }

    private fun startListening() {
        binding.statusText.setText(R.string.listening)
        speechInput.startListening(object : SpeechInputManager.Listener {
            override fun onFinalText(text: String) {
                binding.statusText.setText(R.string.mic_hint)
                appendLine("Aap: $text")
                handleUserText(text)
            }

            override fun onError(message: String) {
                binding.statusText.setText(R.string.mic_hint)
                appendLine("Saathi: Sun nahi paya ($message)")
            }
        })
    }

    private fun onSendPressed() {
        val text = binding.textInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.textInput.setText("")
        appendLine("Aap: $text")
        handleUserText(text)
    }

    private fun handleUserText(text: String) {
        binding.statusText.setText(R.string.thinking)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.chat(ChatRequest(sessionId, text))
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    fallbackToLocalParsing(text, "Backend se jawab nahi mila (HTTP ${response.code()})")
                    return@launch
                }

                appendLine("Saathi: ${body.reply_text}")
                speak(body.reply_text)

                body.actions.forEach { dto -> handleActionDto(dto) }
            } catch (e: IOException) {
                fallbackToLocalParsing(text, "Backend se connect nahi ho paya: ${e.message}")
            } catch (e: Exception) {
                appendLine("Saathi: Kuch gadbad hui: ${e.message}")
            } finally {
                binding.statusText.setText(R.string.mic_hint)
            }
        }
    }

    private fun fallbackToLocalParsing(originalText: String, reasonShown: String) {
        appendLine("Saathi: $reasonShown")
        val localAction = LocalIntentParser.parse(originalText)
        if (localAction == null) {
            appendLine("Saathi: Offline mein ye samajh nahi paya. Internet check karo.")
            return
        }
        appendLine("Saathi: (offline mode) samajh gaya — ${localAction.type}")
        handleActionDto(localAction)
    }

    private fun handleActionDto(dto: ActionDto) {
        if (dto.type.equals("WEB_SEARCH", ignoreCase = true)) {
            val query = dto.params["query"].orEmpty()
            if (query.isNotBlank()) runWebResearch(query)
            return
        }

        val mapped = ActionMapper.map(dto)
        if (mapped is MappedAction.Invalid) {
            appendLine("Saathi: Action samajh nahi aaya (${mapped.reason})")
            return
        }
        if (mapped is MappedAction.None) return

        confirmAndExecute(mapped)
    }

    private fun confirmAndExecute(action: MappedAction) {
        val missingPermissions = PermissionUtils.missingPermissions(this, action)
        if (missingPermissions.isNotEmpty()) {
            pendingPermissionAction = action
            permissionRequest.launch(missingPermissions.toTypedArray())
            return
        }

        if (action.requiresConfirmation) {
            val message = action.confirmationPrompt ?: getString(R.string.confirm_title)
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm_yes) { _, _ -> dispatchConfirmedAction(action) }
                .setNegativeButton(R.string.confirm_no) { _, _ -> appendLine("Saathi: Cancel kar diya.") }
                .show()
        } else {
            dispatchConfirmedAction(action)
        }
    }

    private fun dispatchConfirmedAction(action: MappedAction) {
        if (action is MappedAction.AgentTask) {
            launchAgentTask(action)
        } else {
            runExecutor(action)
        }
    }

    private fun runExecutor(action: MappedAction) {
        val result = actionExecutor.execute(action)
        if (result.message.isNotBlank()) {
            appendLine("Saathi: ${result.message}")
            speak(result.message)
        }
    }

    private fun launchAgentTask(action: MappedAction.AgentTask) {
        lifecycleScope.launch {
            val openResult = actionExecutor.execute(MappedAction.OpenApp(action.appName))
            appendLine("Saathi: ${openResult.message}")
            if (!openResult.success) return@launch

            appendLine("Saathi: '${action.goal}' try kar rahi hoon...")

            val runner = AgentRunner()
            runner.run(action.goal, object : AgentRunner.Listener {
                override fun onLog(line: String) {
                    appendLine(line)
                }

                override suspend fun confirmSensitiveStep(description: String): Boolean {
                    return showConfirmDialog(description)
                }

                override fun onFinished(success: Boolean, message: String) {
                    appendLine("Saathi: $message")
                    speak(message)
                }
            })
        }
    }

    private suspend fun showConfirmDialog(message: String): Boolean = suspendCancellableCoroutine { cont ->
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_yes) { _, _ -> if (cont.isActive) cont.resume(true) }
            .setNegativeButton(R.string.confirm_no) { _, _ -> if (cont.isActive) cont.resume(false) }
            .setOnCancelListener { if (cont.isActive) cont.resume(false) }
            .show()
    }

    private fun runWebResearch(query: String) {
        lifecycleScope.launch {
            appendLine("Saathi: '$query' research kar raha hoon…")
            try {
                val response = RetrofitClient.api.research(ResearchRequest(query))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    appendLine("Saathi: ${body.summary}")
                    speak(body.summary)
                } else {
                    appendLine("Saathi: Research backend se jawab nahi mila (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                appendLine("Saathi: Research fail ho gaya: ${e.message}")
            }
        }
    }

    private fun speak(text: String) {
        if (text.isNotBlank()) ttsManager.speak(text)
    }

    private fun appendLine(line: String) {
        conversationLog.append(line).append("\n\n")
        binding.conversationText.text = conversationLog.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechInput.destroy()
        ttsManager.destroy()
    }
}
