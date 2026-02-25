package com.survey.sync.data

import com.survey.sync.core.SyncStatus
import com.survey.sync.domain.SurveyRepository
import com.survey.sync.domain.SurveyResponse

/**
 * Implementation of SurveyRepository using Room database.
 */
class SurveyRepositoryImpl(
    private val dao: SurveyResponseDao
) : SurveyRepository {

    override suspend fun save(response: SurveyResponse) {
        dao.insert(SurveyResponseEntity.fromDomain(response))
    }

    override suspend fun getById(id: String): SurveyResponse? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun getPendingResponses(): List<SurveyResponse> {
        return dao.getPendingResponses().map { it.toDomain() }
    }

    override suspend fun updateStatus(
        id: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ) {
        dao.updateStatus(id, status, retryCount, lastAttemptAt)
    }

    override suspend fun markSynced(id: String) {
        dao.markSynced(id)
    }

    override suspend fun deleteSyncedBefore(timestamp: Long) {
        dao.deleteSyncedBefore(timestamp)
    }
}
