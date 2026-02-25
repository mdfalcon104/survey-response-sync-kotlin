package com.survey.sync.core

/**
 * Unified error model for sync operations.
 * Classifies all possible errors that can occur during sync.
 */
sealed class SyncError {

    data object NoInternet : SyncError()

    data object Timeout : SyncError()

    data class ServerError(val code: Int, val message: String? = null) : SyncError()

    data class Serialization(val cause: Throwable? = null) : SyncError()

    data class Unknown(val cause: Throwable? = null) : SyncError()

    /**
     * Determines if this error is retryable.
     *
     * Retryable errors:
     * - NoInternet: Network may become available
     * - Timeout: Server may respond next time
     * - ServerError 5xx: Server issues are often temporary
     *
     * Non-retryable errors:
     * - ServerError 4xx: Client errors indicate bad request data
     * - Serialization: Data format issues won't fix themselves
     * - Unknown: Cannot determine if retry will help
     */
    fun isRetryable(): Boolean = when (this) {
        is NoInternet -> true
        is Timeout -> true
        is ServerError -> code in 500..599
        is Serialization -> false
        is Unknown -> false
    }
}
