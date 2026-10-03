package com.saathi.assistant.actions

/** What actually happened — ActionExecutor must never report success unless this says so. */
data class ExecutionResult(val success: Boolean, val message: String) {
    companion object {
        fun ok(message: String) = ExecutionResult(true, message)
        fun fail(message: String) = ExecutionResult(false, message)
    }
}
