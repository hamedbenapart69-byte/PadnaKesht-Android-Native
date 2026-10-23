package com.padnakasht.nativeapp.ui

/** Outcome of a user-visible operation message, used to pick a haptic pattern. */
enum class OperationFeedback { Success, Failure, None }

/**
 * Classifies the Persian status messages shown after a save/pay/return/adjust action.
 *
 * Only clear outcomes produce feedback; validation prompts ("enter a valid amount") and
 * "operation in progress" notices stay silent so the phone does not buzz while the user types.
 * This is presentation-only: it never inspects or changes accounting data.
 */
fun classifyOperationMessage(text: String?): OperationFeedback = when {
    text.isNullOrBlank() -> OperationFeedback.None
    text.contains("ناموفق") || text.contains("نشد") -> OperationFeedback.Failure
    text.contains(" شد") -> OperationFeedback.Success
    else -> OperationFeedback.None
}
