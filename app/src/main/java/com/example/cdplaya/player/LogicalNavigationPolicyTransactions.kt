package com.example.cdplaya.player

import androidx.media3.common.Player

internal fun navigationRepeatModeTraceValue(repeatMode: Int): String =
    when (repeatMode) {
        Player.REPEAT_MODE_OFF -> "REPEAT_MODE_OFF"
        Player.REPEAT_MODE_ONE -> "REPEAT_MODE_ONE"
        Player.REPEAT_MODE_ALL -> "REPEAT_MODE_ALL"
        else -> "UNKNOWN_REPEAT_MODE($repeatMode)"
    }

internal enum class LogicalNavigationPolicyOperation {
    SET_REPEAT_MODE,
    SET_SHUFFLE_MODE
}

internal data class ClaimedInternalNavigationPolicyCommand(
    val transactionId: Long,
    val currentMediaId: String,
    val operation: LogicalNavigationPolicyOperation,
    val value: String
)

/** Exact source provenance for handoff-owned Repeat/Shuffle MediaController commands. */
internal object LogicalNavigationPolicyTransactions {
    private sealed interface ExpectedCommand {
        val operation: LogicalNavigationPolicyOperation
        val traceValue: String

        data class RepeatMode(val repeatMode: Int) : ExpectedCommand {
            override val operation =
                LogicalNavigationPolicyOperation.SET_REPEAT_MODE
            override val traceValue: String =
                navigationRepeatModeTraceValue(repeatMode)
        }

        data class ShuffleMode(val enabled: Boolean) : ExpectedCommand {
            override val operation =
                LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE
            override val traceValue: String = enabled.toString()
        }
    }

    internal data class Token(
        val id: Long,
        val currentMediaId: String
    )

    private data class Transaction(
        val token: Token,
        val expectedCommands: ArrayDeque<ExpectedCommand> = ArrayDeque(),
        var sealed: Boolean = false
    )

    private var nextId = 0L
    private val transactions = LinkedHashMap<Long, Transaction>()

    @Synchronized
    fun begin(currentMediaId: String): Token {
        val token = Token(id = ++nextId, currentMediaId = currentMediaId)
        transactions[token.id] = Transaction(token)
        CrossfadeTrace.log(
            "NAV_POLICY_TX SOURCE_BEGIN id=${token.id} incoming=$currentMediaId"
        )
        return token
    }

    @Synchronized
    fun expectShuffleMode(token: Token, enabled: Boolean) {
        transaction(token)?.expectedCommands?.addLast(
            ExpectedCommand.ShuffleMode(enabled)
        )
    }

    @Synchronized
    fun expectRepeatMode(token: Token, repeatMode: Int) {
        transaction(token)?.expectedCommands?.addLast(
            ExpectedCommand.RepeatMode(repeatMode)
        )
    }

    @Synchronized
    fun seal(token: Token) {
        val current = transaction(token) ?: return
        current.sealed = true
        removeIfComplete(current)
    }

    @Synchronized
    fun claimShuffleMode(
        currentMediaId: String?,
        enabled: Boolean
    ): ClaimedInternalNavigationPolicyCommand? = claim(
        currentMediaId = currentMediaId,
        actual = ExpectedCommand.ShuffleMode(enabled)
    )

    @Synchronized
    fun claimRepeatMode(
        currentMediaId: String?,
        repeatMode: Int
    ): ClaimedInternalNavigationPolicyCommand? = claim(
        currentMediaId = currentMediaId,
        actual = ExpectedCommand.RepeatMode(repeatMode)
    )

    @Synchronized
    fun isActive(transactionId: Long): Boolean =
        transactions.containsKey(transactionId)

    @Synchronized
    fun activeTransactionIdsFor(currentMediaId: String): Set<Long> =
        transactions.values.filter { current ->
            current.token.currentMediaId == currentMediaId
        }.mapTo(linkedSetOf()) { current -> current.token.id }

    @Synchronized
    fun abort(transactionId: Long, reason: String) {
        if (transactions.remove(transactionId) == null) return
        CrossfadeTrace.log("NAV_POLICY_TX ABORT id=$transactionId reason=$reason")
    }

    @Synchronized
    fun abortAll(reason: String) {
        val ids = transactions.keys.toList()
        transactions.clear()
        ids.forEach { id ->
            CrossfadeTrace.log("NAV_POLICY_TX ABORT id=$id reason=$reason")
        }
    }

    @Synchronized
    fun clearForTest() {
        transactions.clear()
        nextId = 0L
    }

    private fun claim(
        currentMediaId: String?,
        actual: ExpectedCommand
    ): ClaimedInternalNavigationPolicyCommand? {
        if (transactions.isEmpty()) return null
        val current = transactions.values.firstOrNull { transaction ->
            currentMediaId == transaction.token.currentMediaId &&
                transaction.expectedCommands.firstOrNull() == actual
        }
        if (current == null) {
            // A genuine controller command supersedes every still-unclaimed handoff command.
            // Clearing the full set prevents an older media-id transaction from poisoning a
            // later exact user command.
            abortAll("command_mismatch")
            return null
        }
        transactions.values
            .filter { transaction ->
                transaction.token.currentMediaId != currentMediaId
            }
            .map { transaction -> transaction.token.id }
            .forEach { transactionId ->
                abort(transactionId, "media_identity_superseded")
            }
        val expected = checkNotNull(current.expectedCommands.firstOrNull())
        current.expectedCommands.removeFirst()
        val claim = ClaimedInternalNavigationPolicyCommand(
            transactionId = current.token.id,
            currentMediaId = current.token.currentMediaId,
            operation = expected.operation,
            value = expected.traceValue
        )
        removeIfComplete(current)
        return claim
    }

    private fun transaction(token: Token): Transaction? =
        transactions[token.id]?.takeIf { current -> current.token == token }

    private fun removeIfComplete(current: Transaction) {
        if (!current.sealed || current.expectedCommands.isNotEmpty()) return
        transactions.remove(current.token.id)
        CrossfadeTrace.log("NAV_POLICY_TX SOURCE_COMPLETE id=${current.token.id}")
    }
}
