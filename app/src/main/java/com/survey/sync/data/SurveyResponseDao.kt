package com.survey.sync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.survey.sync.core.SyncStatus

/**
 * Data Access Object for survey responses.
 */
@Dao
interface SurveyResponseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SurveyResponseEntity)

    @Query("SELECT * FROM survey_responses WHERE id = :id")
    suspend fun getById(id: String): SurveyResponseEntity?

    @Query("""
        SELECT * FROM survey_responses
        WHERE status = 'PENDING' OR status = 'FAILED_RETRYABLE'
        ORDER BY createdAt ASC
    """)
    suspend fun getPendingResponses(): List<SurveyResponseEntity>

    @Query("""
        UPDATE survey_responses
        SET status = :status, retryCount = :retryCount, lastAttemptAt = :lastAttemptAt
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: SyncStatus, retryCount: Int, lastAttemptAt: Long)

    @Query("""
        UPDATE survey_responses
        SET status = 'SYNCED', mediaFilePaths = '[]'
        WHERE id = :id
    """)
    suspend fun markSynced(id: String)

    @Query("DELETE FROM survey_responses WHERE status = 'SYNCED' AND createdAt < :timestamp")
    suspend fun deleteSyncedBefore(timestamp: Long)

    @Query("DELETE FROM survey_responses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM survey_responses")
    suspend fun count(): Int
}
