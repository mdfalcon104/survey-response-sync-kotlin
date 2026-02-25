package com.survey.sync.domain

import com.survey.sync.core.SyncStatus

/**
 * Domain entity representing a survey response.
 *
 * This is the core business entity used throughout the application.
 * Answers are stored as JSON string to support flexible survey structures
 * including nested objects and repeating sections.
 *
 * Example answersJson for a farmer with 3 farms (repeating section):
 * ```json
 * {
 *   "farmerName": "John Doe",
 *   "totalFarms": 3,
 *   "farms": [
 *     {
 *       "farmIndex": 1,
 *       "cropType": "maize",
 *       "areaHectares": 2.5,
 *       "yieldEstimate": 1500,
 *       "gpsBoundary": [[lat1,lon1], [lat2,lon2], ...]
 *     },
 *     {
 *       "farmIndex": 2,
 *       "cropType": "wheat",
 *       "areaHectares": 1.8,
 *       "yieldEstimate": 900,
 *       "gpsBoundary": [[lat1,lon1], [lat2,lon2], ...]
 *     },
 *     {
 *       "farmIndex": 3,
 *       "cropType": "sorghum",
 *       "areaHectares": 3.2,
 *       "yieldEstimate": 2000,
 *       "gpsBoundary": [[lat1,lon1], [lat2,lon2], ...]
 *     }
 *   ]
 * }
 * ```
 *
 * The number of repetitions (farms array length) is determined by prior answers
 * (totalFarms field) and is not known at survey design time.
 */
data class SurveyResponse(
    val id: String,
    val farmerId: String,
    val createdAt: Long,
    val answersJson: String,
    val status: SyncStatus,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val mediaFilePaths: List<String>
) {
    companion object {
        const val MAX_RETRY_COUNT = 5
    }

    /**
     * Check if this response can still be retried.
     */
    fun canRetry(): Boolean = retryCount < MAX_RETRY_COUNT

    /**
     * Check if this response should be included in sync queue.
     */
    fun needsSync(): Boolean = status == SyncStatus.PENDING || status == SyncStatus.FAILED_RETRYABLE
}
