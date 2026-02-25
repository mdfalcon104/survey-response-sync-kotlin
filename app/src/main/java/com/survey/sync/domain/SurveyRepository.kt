package com.survey.sync.domain

import com.survey.sync.core.SyncStatus

/**
 * Repository interface for survey response operations.
 */
interface SurveyRepository {

    /**
     * Save a new survey response locally.
     */
    suspend fun save(response: SurveyResponse)

    /**
     * Get a survey response by ID.
     */
    suspend fun getById(id: String): SurveyResponse?

    /**
     * Get all responses that need to be synced (PENDING or FAILED_RETRYABLE).
     */
    suspend fun getPendingResponses(): List<SurveyResponse>

    /**
     * Update the sync status of a response.
     */
    suspend fun updateStatus(
        id: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    )

    /**
     * Mark a response as synced and clear media paths.
     */
    suspend fun markSynced(id: String)

    /**
     * Delete all synced responses older than given timestamp.
     */
    suspend fun deleteSyncedBefore(timestamp: Long)
}
