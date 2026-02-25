package com.survey.sync.core

/**
 * Status of a survey response in the sync queue.
 */
enum class SyncStatus {
    /**
     * Response is waiting to be synced.
     */
    PENDING,

    /**
     * Response has been successfully synced to server.
     */
    SYNCED,

    /**
     * Sync failed but can be retried (e.g., network issue, server 5xx).
     */
    FAILED_RETRYABLE,

    /**
     * Sync failed permanently (e.g., exceeded retry limit, server 4xx).
     */
    FAILED_PERMANENT
}
