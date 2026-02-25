package com.survey.sync

import com.google.common.truth.Truth.assertThat
import com.survey.sync.core.SyncStatus
import com.survey.sync.domain.CleanupConfig
import com.survey.sync.domain.StorageCleanupManager
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests for StorageCleanupManager.
 */
class StorageCleanupManagerTest {

    private lateinit var repository: FakeSurveyRepository
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var cleanupManager: StorageCleanupManager

    @Before
    fun setup() {
        repository = FakeSurveyRepository()
        timeProvider = FakeTimeProvider(currentTime = 1000000000L)
        cleanupManager = StorageCleanupManager(
            repository = repository,
            timeProvider = timeProvider,
            config = CleanupConfig(
                retentionPeriodMs = 7 * 24 * 60 * 60 * 1000L, // 7 days
                cleanupThreshold = 30
            )
        )
    }

    @Test
    fun `cleanup deletes old synced responses`() = runTest {
        // Old synced response (created 10 days ago)
        val oldSynced = TestHelpers.createSurveyResponse(
            id = "old-synced",
            status = SyncStatus.SYNCED,
            createdAt = timeProvider.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L)
        )
        repository.save(oldSynced)

        // Recent synced response (created 2 days ago)
        val recentSynced = TestHelpers.createSurveyResponse(
            id = "recent-synced",
            status = SyncStatus.SYNCED,
            createdAt = timeProvider.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L)
        )
        repository.save(recentSynced)

        // Pending response (should never be deleted)
        val pending = TestHelpers.createSurveyResponse(
            id = "pending",
            status = SyncStatus.PENDING,
            createdAt = timeProvider.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L)
        )
        repository.save(pending)

        cleanupManager.cleanupOldSyncedResponses()

        assertThat(repository.getById("old-synced")).isNull()
        assertThat(repository.getById("recent-synced")).isNotNull()
        assertThat(repository.getById("pending")).isNotNull()
    }

    @Test
    fun `shouldTriggerCleanup returns true when threshold exceeded`() = runTest {
        assertThat(cleanupManager.shouldTriggerCleanup(35)).isTrue()
        assertThat(cleanupManager.shouldTriggerCleanup(30)).isTrue()
    }

    @Test
    fun `shouldTriggerCleanup returns false below threshold`() = runTest {
        assertThat(cleanupManager.shouldTriggerCleanup(29)).isFalse()
        assertThat(cleanupManager.shouldTriggerCleanup(10)).isFalse()
    }
}
