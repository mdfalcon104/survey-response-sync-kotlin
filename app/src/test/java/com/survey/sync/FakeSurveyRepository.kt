package com.survey.sync

import com.survey.sync.core.SyncStatus
import com.survey.sync.domain.SurveyRepository
import com.survey.sync.domain.SurveyResponse

/**
 * In-memory repository for unit testing.
 */
class FakeSurveyRepository : SurveyRepository {

    private val responses = mutableMapOf<String, SurveyResponse>()

    override suspend fun save(response: SurveyResponse) {
        responses[response.id] = response
    }

    override suspend fun getById(id: String): SurveyResponse? {
        return responses[id]
    }

    override suspend fun getPendingResponses(): List<SurveyResponse> {
        return responses.values
            .filter { it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED_RETRYABLE }
            .sortedBy { it.createdAt }
    }

    override suspend fun updateStatus(
        id: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ) {
        responses[id]?.let { response ->
            responses[id] = response.copy(
                status = status,
                retryCount = retryCount,
                lastAttemptAt = lastAttemptAt
            )
        }
    }

    override suspend fun markSynced(id: String) {
        responses[id]?.let { response ->
            responses[id] = response.copy(
                status = SyncStatus.SYNCED,
                mediaFilePaths = emptyList()
            )
        }
    }

    override suspend fun deleteSyncedBefore(timestamp: Long) {
        responses.entries.removeIf { (_, response) ->
            response.status == SyncStatus.SYNCED && response.createdAt < timestamp
        }
    }

    /**
     * Clear all responses. For testing only.
     */
    fun clear() {
        responses.clear()
    }

    /**
     * Get all responses. For testing only.
     */
    fun getAll(): List<SurveyResponse> = responses.values.toList()
}
