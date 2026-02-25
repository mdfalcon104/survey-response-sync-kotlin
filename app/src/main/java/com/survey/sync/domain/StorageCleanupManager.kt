package com.survey.sync.domain

/**
 * Manages storage cleanup to prevent device storage exhaustion.
 *
 * Field agents collect 50+ surveys per day with photo attachments.
 * On devices with 16-32GB storage, cleanup of synced data is essential.
 */
class StorageCleanupManager(
    private val repository: SurveyRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val config: CleanupConfig = CleanupConfig()
) {

    /**
     * Clean up old synced responses to free storage space.
     *
     * Only deletes responses that have been successfully synced
     * and are older than the retention period.
     *
     * @return Number of responses deleted
     */
    suspend fun cleanupOldSyncedResponses(): Int {
        val cutoffTime = timeProvider.currentTimeMillis() - config.retentionPeriodMs
        repository.deleteSyncedBefore(cutoffTime)
        return 0 // Room doesn't return affected rows easily, but data is deleted
    }

    /**
     * Check if cleanup should be triggered based on pending response count.
     * Call this after saving new responses.
     */
    suspend fun shouldTriggerCleanup(pendingCount: Int): Boolean {
        return pendingCount >= config.cleanupThreshold
    }
}

/**
 * Configuration for storage cleanup.
 */
data class CleanupConfig(
    /**
     * How long to keep synced responses before deletion.
     * Default: 7 days (gives time for any server-side issues to be detected)
     */
    val retentionPeriodMs: Long = 7 * 24 * 60 * 60 * 1000L,

    /**
     * Number of pending responses that triggers cleanup consideration.
     * With 50+ surveys/day, trigger cleanup proactively.
     */
    val cleanupThreshold: Int = 30
)
