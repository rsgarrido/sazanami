package io.github.rsgarrido.sazanami.player

internal enum class UsbActivationStage { REQUEST, PREFLIGHT, SNAPSHOT, RELEASE_NORMAL, SELECT_DIRECT, ACTIVE, ATTACHMENT_LOST, RESTORE_ANDROID, SHUTDOWN }
internal enum class UsbActivationOutput { ANDROID, DIRECT, SWITCHING, BLOCKED }
internal enum class UsbActivationReservation { NOT_ACQUIRED, RESERVED, PROTECTED, RELEASED, RELEASE_UNVERIFIED }
internal enum class UsbActivationRestoration { NOT_REQUIRED, PENDING, COMPLETED, BLOCKED }

/** One bounded service snapshot. No device identifiers, source metadata or exception messages. */
internal data class UsbActivationEvidence(
    val serial: Long,
    val stage: UsbActivationStage = UsbActivationStage.REQUEST,
    val failureStage: UsbActivationStage? = null,
    val failure: String? = null,
    val exceptionClass: String? = null,
    val output: UsbActivationOutput = UsbActivationOutput.ANDROID,
    val reservation: UsbActivationReservation = UsbActivationReservation.NOT_ACQUIRED,
    val restoration: UsbActivationRestoration = UsbActivationRestoration.NOT_REQUIRED,
    val restorationFailure: String? = null
)

internal fun formatUsbActivationEvidence(value: UsbActivationEvidence): String = buildString {
    appendLine("USB output activation attempt=${value.serial}")
    appendLine("stage=${value.stage}; failure stage=${value.failureStage ?: "none"}")
    appendLine("reason=${value.failure ?: "none"}; exception class=${value.exceptionClass ?: "none"}")
    appendLine("output selection=${value.output}; reservation=${value.reservation}")
    appendLine("Android restoration=${value.restoration}; restoration reason=${value.restorationFailure ?: "none"}")
}
