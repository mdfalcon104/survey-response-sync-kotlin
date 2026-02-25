package com.survey.sync.core

/**
 * Result of a sync operation.
 * Contains detailed information about what happened during sync.
 */
data class SyncResult(
    val succeededIds: List<String>,
    val failedIds: List<String>,
    val pendingIds: List<String>,
    val stopReason: StopReason? = null
) {
    val totalProcessed: Int get() = succeededIds.size + failedIds.size
    val hasFailures: Boolean get() = failedIds.isNotEmpty()
    val wasStoppedEarly: Boolean get() = stopReason != null

    companion object {
        fun empty() = SyncResult(
            succeededIds = emptyList(),
            failedIds = emptyList(),
            pendingIds = emptyList()
        )
    }
}

/**
 * Reason why sync was stopped early.
 */
sealed class StopReason {
    data class NetworkDegradation(val consecutiveFailures: Int) : StopReason()
    data class FatalError(val error: SyncError) : StopReason()
    data object Cancelled : StopReason()
}
