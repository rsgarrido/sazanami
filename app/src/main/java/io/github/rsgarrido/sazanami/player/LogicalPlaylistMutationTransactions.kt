package io.github.rsgarrido.sazanami.player

internal enum class LogicalPlaylistMutationOperation {
    REMOVE_PREFIX,
    REPLACE_UPCOMING,
    OTHER
}

internal data class ClaimedInternalPlaylistMutation(
    val transactionId: Long,
    val currentMediaId: String,
    val operation: LogicalPlaylistMutationOperation
)

/**
 * Source-owned provenance for MediaController playlist commands. Commands can reach the service
 * after their initiating call returns, so provenance remains queued until every exact operation
 * has been claimed by the logical Player or the transaction is explicitly aborted.
 */
internal object LogicalPlaylistMutationTransactions {
    private sealed interface ExpectedOperation {
        val operation: LogicalPlaylistMutationOperation

        data class RemovePrefix(
            val fromIndex: Int,
            val toIndex: Int
        ) : ExpectedOperation {
            override val operation =
                LogicalPlaylistMutationOperation.REMOVE_PREFIX
        }

        data class ReplaceUpcoming(
            val fromIndex: Int,
            val toIndex: Int,
            val mediaIds: List<String>
        ) : ExpectedOperation {
            override val operation =
                LogicalPlaylistMutationOperation.REPLACE_UPCOMING
        }
    }

    internal data class Token(
        val id: Long,
        val currentMediaId: String
    )

    private data class Transaction(
        val token: Token,
        val expectedOperations: ArrayDeque<ExpectedOperation> = ArrayDeque(),
        var sealed: Boolean = false
    )

    private var nextId = 0L
    private val transactions = LinkedHashMap<Long, Transaction>()

    @Synchronized
    fun begin(currentMediaId: String): Token {
        if (transactions.isNotEmpty()) {
            abortAll("superseded_by_source_transaction")
        }
        val token = Token(id = ++nextId, currentMediaId = currentMediaId)
        transactions[token.id] = Transaction(token)
        CrossfadeTrace.log(
            "HANDOFF_TX SOURCE_BEGIN id=${token.id} incoming=$currentMediaId"
        )
        return token
    }

    @Synchronized
    fun expectRemovePrefix(token: Token, fromIndex: Int, toIndex: Int) {
        transaction(token)?.expectedOperations?.addLast(
            ExpectedOperation.RemovePrefix(fromIndex, toIndex)
        )
    }

    @Synchronized
    fun expectReplaceUpcoming(
        token: Token,
        fromIndex: Int,
        toIndex: Int,
        mediaIds: List<String>
    ) {
        transaction(token)?.expectedOperations?.addLast(
            ExpectedOperation.ReplaceUpcoming(fromIndex, toIndex, mediaIds)
        )
    }

    @Synchronized
    fun seal(token: Token) {
        val transaction = transaction(token) ?: return
        transaction.sealed = true
        removeIfComplete(transaction)
    }

    @Synchronized
    fun claimRemovePrefix(
        currentMediaId: String?,
        fromIndex: Int,
        toIndex: Int
    ): ClaimedInternalPlaylistMutation? = claim(
        currentMediaId = currentMediaId,
        actual = ExpectedOperation.RemovePrefix(fromIndex, toIndex)
    )

    @Synchronized
    fun claimReplaceUpcoming(
        currentMediaId: String?,
        fromIndex: Int,
        toIndex: Int,
        mediaIds: List<String>
    ): ClaimedInternalPlaylistMutation? = claim(
        currentMediaId = currentMediaId,
        actual = ExpectedOperation.ReplaceUpcoming(fromIndex, toIndex, mediaIds)
    )

    @Synchronized
    fun isActive(transactionId: Long): Boolean =
        transactions.containsKey(transactionId)

    @Synchronized
    fun activeTransactionIdFor(currentMediaId: String): Long? =
        transactions.values.firstOrNull { transaction ->
            transaction.token.currentMediaId == currentMediaId
        }?.token?.id

    @Synchronized
    fun abort(transactionId: Long, reason: String) {
        if (transactions.remove(transactionId) != null) {
            CrossfadeTrace.log("HANDOFF_TX ABORT id=$transactionId reason=$reason")
        }
    }

    @Synchronized
    fun abortAll(reason: String) {
        val ids = transactions.keys.toList()
        transactions.clear()
        ids.forEach { id ->
            CrossfadeTrace.log("HANDOFF_TX ABORT id=$id reason=$reason")
        }
    }

    @Synchronized
    fun clearForTest() {
        transactions.clear()
        nextId = 0L
    }

    private fun claim(
        currentMediaId: String?,
        actual: ExpectedOperation
    ): ClaimedInternalPlaylistMutation? {
        val transaction = transactions.values.firstOrNull() ?: return null
        val expected = transaction.expectedOperations.firstOrNull()
        if (
            currentMediaId != transaction.token.currentMediaId ||
            expected != actual
        ) {
            abort(transaction.token.id, reason = "operation_mismatch")
            return null
        }
        transaction.expectedOperations.removeFirst()
        val claim = ClaimedInternalPlaylistMutation(
            transactionId = transaction.token.id,
            currentMediaId = transaction.token.currentMediaId,
            operation = expected.operation
        )
        removeIfComplete(transaction)
        return claim
    }

    private fun transaction(token: Token): Transaction? =
        transactions[token.id]?.takeIf { transaction ->
            transaction.token == token
        }

    private fun removeIfComplete(transaction: Transaction) {
        if (!transaction.sealed || transaction.expectedOperations.isNotEmpty()) return
        transactions.remove(transaction.token.id)
        CrossfadeTrace.log("HANDOFF_TX SOURCE_COMPLETE id=${transaction.token.id}")
    }
}
