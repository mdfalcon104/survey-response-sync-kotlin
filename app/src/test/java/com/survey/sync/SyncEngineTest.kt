package com.survey.sync

import com.google.common.truth.Truth.assertThat
import com.survey.sync.core.StopReason
import com.survey.sync.core.SyncError
import com.survey.sync.core.SyncStatus
import com.survey.sync.domain.SyncConfig
import com.survey.sync.domain.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive tests for SyncEngine.
 * Tests all required scenarios from the specification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private lateinit var repository: FakeSurveyRepository
    private lateinit var api: FakeSurveyApi
    private lateinit var storageManager: FakeStorageManager
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var syncEngine: SyncEngine

    @Before
    fun setup() {
        repository = FakeSurveyRepository()
        api = FakeSurveyApi()
        storageManager = FakeStorageManager()
        timeProvider = FakeTimeProvider()
        syncEngine = SyncEngine(
            repository = repository,
            api = api,
            storageManager = storageManager,
            timeProvider = timeProvider
        )
    }

    // ==================== Scenario 1: All Succeed ====================

    @Test
    fun `sync with all responses succeeding marks all as SYNCED`() = runTest {
        val responses = TestHelpers.createSurveyResponses(5)
        responses.forEach { repository.save(it) }

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysSuccess)

        val result = syncEngine.sync()

        assertThat(result.succeededIds).hasSize(5)
        assertThat(result.failedIds).isEmpty()
        assertThat(result.pendingIds).isEmpty()
        assertThat(result.stopReason).isNull()

        responses.forEach { response ->
            val updated = repository.getById(response.id)
            assertThat(updated?.status).isEqualTo(SyncStatus.SYNCED)
        }
    }

    @Test
    fun `sync uploads all responses to API`() = runTest {
        val responses = TestHelpers.createSurveyResponses(3)
        responses.forEach { repository.save(it) }

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysSuccess)

        syncEngine.sync()

        assertThat(api.getUploadedResponses()).hasSize(3)
        assertThat(api.getCallCount()).isEqualTo(3)
    }

    // ==================== Scenario 2: Partial Failure ====================

    @Test
    fun `partial failure marks first five as SYNCED and sixth as FAILED_RETRYABLE`() = runTest {
        val responses = TestHelpers.createSurveyResponses(8)
        responses.forEach { repository.save(it) }

        api.setBehavior(
            FakeSurveyApi.ApiBehavior.FailOnCall(
                failOnCalls = setOf(6),
                error = SyncError.ServerError(500)
            )
        )

        // Use threshold of 1 to stop after first failure (Scenario 2 requirement)
        val engine = SyncEngine(
            repository, api, storageManager, timeProvider,
            SyncConfig(consecutiveFailureThreshold = 1)
        )
        val result = engine.sync()

        assertThat(result.succeededIds).hasSize(5)
        assertThat(result.failedIds).containsExactly("response-6")
        assertThat(result.pendingIds).containsExactly("response-7", "response-8")

        for (i in 1..5) {
            val response = repository.getById("response-$i")
            assertThat(response?.status).isEqualTo(SyncStatus.SYNCED)
        }

        val failedResponse = repository.getById("response-6")
        assertThat(failedResponse?.status).isEqualTo(SyncStatus.FAILED_RETRYABLE)

        for (i in 7..8) {
            val response = repository.getById("response-$i")
            assertThat(response?.status).isEqualTo(SyncStatus.PENDING)
        }
    }

    @Test
    fun `SyncResult contains correct succeeded and failed ids`() = runTest {
        val responses = TestHelpers.createSurveyResponses(5)
        responses.forEach { repository.save(it) }

        api.setBehavior(
            FakeSurveyApi.ApiBehavior.FailOnCall(
                failOnCalls = setOf(3),
                error = SyncError.ServerError(500)
            )
        )

        // Use threshold of 1 to stop after first failure
        val engine = SyncEngine(
            repository, api, storageManager, timeProvider,
            SyncConfig(consecutiveFailureThreshold = 1)
        )
        val result = engine.sync()

        assertThat(result.succeededIds).containsExactly("response-1", "response-2")
        assertThat(result.failedIds).containsExactly("response-3")
        assertThat(result.pendingIds).containsExactly("response-4", "response-5")
    }

    @Test
    fun `next sync retries only failed and pending items`() = runTest {
        val responses = TestHelpers.createSurveyResponses(5)
        responses.forEach { repository.save(it) }

        api.setBehavior(
            FakeSurveyApi.ApiBehavior.FailOnCall(
                failOnCalls = setOf(3),
                error = SyncError.ServerError(500)
            )
        )

        // Use threshold of 1 to stop after first failure
        val engine = SyncEngine(
            repository, api, storageManager, timeProvider,
            SyncConfig(consecutiveFailureThreshold = 1)
        )
        engine.sync()
        api.reset()
        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysSuccess)

        val secondResult = engine.sync()

        assertThat(secondResult.succeededIds).containsExactly("response-3", "response-4", "response-5")
        assertThat(api.getCallCount()).isEqualTo(3)
    }

    // ==================== Scenario 3: Network Degradation ====================

    @Test
    fun `sync stops early after consecutive timeouts`() = runTest {
        val responses = TestHelpers.createSurveyResponses(10)
        responses.forEach { repository.save(it) }

        var callCount = 0
        api.setBehavior(FakeSurveyApi.ApiBehavior.Custom { _, _ ->
            callCount++
            if (callCount >= 3) {
                Result.failure(com.survey.sync.domain.SyncException(SyncError.Timeout))
            } else {
                Result.success(Unit)
            }
        })

        val config = SyncConfig(consecutiveFailureThreshold = 3)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        val result = engine.sync()

        assertThat(result.wasStoppedEarly).isTrue()
        assertThat(result.stopReason).isInstanceOf(StopReason.NetworkDegradation::class.java)
        assertThat(result.pendingIds).isNotEmpty()
    }

    @Test
    fun `sync stops immediately on NoInternet`() = runTest {
        val responses = TestHelpers.createSurveyResponses(5)
        responses.forEach { repository.save(it) }

        api.setBehavior(
            FakeSurveyApi.ApiBehavior.FailAfterCall(
                succeedUntil = 2,
                error = SyncError.NoInternet
            )
        )

        val result = syncEngine.sync()

        assertThat(result.wasStoppedEarly).isTrue()
        assertThat(result.stopReason).isInstanceOf(StopReason.FatalError::class.java)
        assertThat(result.succeededIds).hasSize(2)
        assertThat(result.pendingIds).containsExactly("response-4", "response-5")
    }

    @Test
    fun `stop reason contains consecutive failure count`() = runTest {
        val responses = TestHelpers.createSurveyResponses(10)
        responses.forEach { repository.save(it) }

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysFail(SyncError.Timeout))

        val config = SyncConfig(consecutiveFailureThreshold = 3)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        val result = engine.sync()

        val stopReason = result.stopReason as StopReason.NetworkDegradation
        assertThat(stopReason.consecutiveFailures).isEqualTo(3)
    }

    // ==================== Scenario 4: Concurrent Sync Prevention ====================

    @Test
    fun `concurrent sync calls share the same result`() = runTest {
        val responses = TestHelpers.createSurveyResponses(5)
        responses.forEach { repository.save(it) }

        var slowApiCalled = false
        api.setBehavior(FakeSurveyApi.ApiBehavior.Custom { _, _ ->
            if (!slowApiCalled) {
                slowApiCalled = true
                delay(100)
            }
            Result.success(Unit)
        })

        val result1 = async { syncEngine.sync() }
        val result2 = async { syncEngine.sync() }

        val r1 = result1.await()
        val r2 = result2.await()

        assertThat(r1).isEqualTo(r2)
        assertThat(api.getCallCount()).isEqualTo(5)
    }

    @Test
    fun `second sync does not cause duplicate uploads`() = runTest {
        val responses = TestHelpers.createSurveyResponses(3)
        responses.forEach { repository.save(it) }

        api.setBehavior(FakeSurveyApi.ApiBehavior.Custom { _, _ ->
            delay(50)
            Result.success(Unit)
        })

        val result1 = async { syncEngine.sync() }
        delay(10)
        val result2 = async { syncEngine.sync() }

        result1.await()
        result2.await()

        assertThat(api.getCallCount()).isEqualTo(3)
    }

    // ==================== Scenario 5: Error Classification ====================

    @Test
    fun `server 400 error marks response as FAILED_PERMANENT`() = runTest {
        val response = TestHelpers.createSurveyResponse(id = "test-1")
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.ServerError400)

        syncEngine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.FAILED_PERMANENT)
    }

    @Test
    fun `server 500 error marks response as FAILED_RETRYABLE`() = runTest {
        val response = TestHelpers.createSurveyResponse(id = "test-1")
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.ServerError500)

        val config = SyncConfig(consecutiveFailureThreshold = 5)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        engine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.FAILED_RETRYABLE)
    }

    @Test
    fun `timeout error marks response as FAILED_RETRYABLE`() = runTest {
        val response = TestHelpers.createSurveyResponse(id = "test-1")
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysFail(SyncError.Timeout))

        val config = SyncConfig(consecutiveFailureThreshold = 5)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        engine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.FAILED_RETRYABLE)
    }

    @Test
    fun `serialization error marks response as FAILED_PERMANENT`() = runTest {
        val response = TestHelpers.createSurveyResponse(id = "test-1")
        repository.save(response)

        api.setBehavior(
            FakeSurveyApi.ApiBehavior.AlwaysFail(SyncError.Serialization(RuntimeException("Bad JSON")))
        )

        syncEngine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.FAILED_PERMANENT)
    }

    // ==================== Retry Logic ====================

    @Test
    fun `retry count increments on each failure`() = runTest {
        val response = TestHelpers.createSurveyResponse(id = "test-1")
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.ServerError500)

        val config = SyncConfig(maxRetryCount = 10, consecutiveFailureThreshold = 10)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        repeat(3) {
            engine.sync()
        }

        val updated = repository.getById("test-1")
        assertThat(updated?.retryCount).isEqualTo(3)
    }

    @Test
    fun `response marked FAILED_PERMANENT after max retries`() = runTest {
        val response = TestHelpers.createSurveyResponse(
            id = "test-1",
            retryCount = 4
        )
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.ServerError500)

        val config = SyncConfig(maxRetryCount = 5, consecutiveFailureThreshold = 10)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        engine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.FAILED_PERMANENT)
        assertThat(updated?.retryCount).isEqualTo(5)
    }

    @Test
    fun `FAILED_PERMANENT responses are not retried`() = runTest {
        val response = TestHelpers.createSurveyResponse(
            id = "test-1",
            status = SyncStatus.FAILED_PERMANENT
        )
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysSuccess)

        val result = syncEngine.sync()

        assertThat(result.succeededIds).isEmpty()
        assertThat(api.getCallCount()).isEqualTo(0)
    }

    @Test
    fun `exponential backoff calculates correct delays`() {
        val engine = SyncEngine(
            repository, api, storageManager, timeProvider,
            SyncConfig(initialBackoffMs = 1000, maxBackoffMs = 60000, maxBackoffExponent = 6)
        )

        assertThat(engine.calculateBackoffDelay(0)).isEqualTo(1000)   // 1000 * 2^0 = 1000
        assertThat(engine.calculateBackoffDelay(1)).isEqualTo(2000)   // 1000 * 2^1 = 2000
        assertThat(engine.calculateBackoffDelay(2)).isEqualTo(4000)   // 1000 * 2^2 = 4000
        assertThat(engine.calculateBackoffDelay(3)).isEqualTo(8000)   // 1000 * 2^3 = 8000
        assertThat(engine.calculateBackoffDelay(10)).isEqualTo(60000) // capped at maxBackoffMs
    }

    // ==================== Empty Queue ====================

    @Test
    fun `sync with empty queue returns empty result`() = runTest {
        val result = syncEngine.sync()

        assertThat(result.succeededIds).isEmpty()
        assertThat(result.failedIds).isEmpty()
        assertThat(result.pendingIds).isEmpty()
        assertThat(result.stopReason).isNull()
        assertThat(api.getCallCount()).isEqualTo(0)
    }

    @Test
    fun `sync with only SYNCED responses returns empty result`() = runTest {
        val response = TestHelpers.createSurveyResponse(
            id = "test-1",
            status = SyncStatus.SYNCED
        )
        repository.save(response)

        val result = syncEngine.sync()

        assertThat(result.succeededIds).isEmpty()
        assertThat(api.getCallCount()).isEqualTo(0)
    }

    // ==================== Media Deletion ====================

    @Test
    fun `media files deleted after successful upload`() = runTest {
        val response = TestHelpers.createSurveyResponse(
            id = "test-1",
            mediaFilePaths = listOf("/path/to/image1.jpg", "/path/to/image2.jpg")
        )
        repository.save(response)
        storageManager.addExistingFiles("/path/to/image1.jpg", "/path/to/image2.jpg")

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysSuccess)

        syncEngine.sync()

        val deletedFiles = storageManager.getDeletedFiles()
        assertThat(deletedFiles).containsExactly("/path/to/image1.jpg", "/path/to/image2.jpg")
    }

    @Test
    fun `media files not deleted on failed upload`() = runTest {
        val response = TestHelpers.createSurveyResponse(
            id = "test-1",
            mediaFilePaths = listOf("/path/to/image.jpg")
        )
        repository.save(response)
        storageManager.addExistingFiles("/path/to/image.jpg")

        api.setBehavior(FakeSurveyApi.ApiBehavior.ServerError500)

        val config = SyncConfig(consecutiveFailureThreshold = 5)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        engine.sync()

        val deletedFiles = storageManager.getDeletedFiles()
        assertThat(deletedFiles).isEmpty()
    }

    @Test
    fun `media paths cleared in database after successful upload`() = runTest {
        val response = TestHelpers.createSurveyResponse(
            id = "test-1",
            mediaFilePaths = listOf("/path/to/image.jpg")
        )
        repository.save(response)

        api.setBehavior(FakeSurveyApi.ApiBehavior.AlwaysSuccess)

        syncEngine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.mediaFilePaths).isEmpty()
    }

    // ==================== Last Attempt Timestamp ====================

    @Test
    fun `lastAttemptAt updated on sync attempt`() = runTest {
        val response = TestHelpers.createSurveyResponse(id = "test-1")
        repository.save(response)

        timeProvider.setTime(1234567890L)
        api.setBehavior(FakeSurveyApi.ApiBehavior.ServerError500)

        val config = SyncConfig(consecutiveFailureThreshold = 5)
        val engine = SyncEngine(repository, api, storageManager, timeProvider, config)

        engine.sync()

        val updated = repository.getById("test-1")
        assertThat(updated?.lastAttemptAt).isEqualTo(1234567890L)
    }
}
