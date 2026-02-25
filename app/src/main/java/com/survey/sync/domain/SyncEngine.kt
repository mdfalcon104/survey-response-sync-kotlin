package com.survey.sync.domain

import com.survey.sync.core.StopReason
import com.survey.sync.core.SyncError
import com.survey.sync.core.SyncResult
import com.survey.sync.core.SyncStatus
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Engine responsible for syncing survey responses to the server.
 *
 * Features:
 * - Sequential upload with early stop on network failure
 * - Concurrent sync prevention using Mutex
 * - Exponential backoff retry logic
 * - Media cleanup after successful upload
 */
class SyncEngine(
    private val repository: SurveyRepository,
    private val api: SurveyApi,
    private val storageManager: StorageManager,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val config: SyncConfig = SyncConfig()
) {
    private val syncMutex = Mutex()
    private var runningJob: Deferred<SyncResult>? = null

    /**
     * Execute sync operation.
     *
     * If sync is already running, this call will wait for and return the same result.
     * Only one sync operation can run at a time to prevent duplicate uploads.
     */
    suspend fun sync(): SyncResult = coroutineScope {
        // First check if there's already a running job (without holding lock long)
        val existingJob = syncMutex.withLock {
            runningJob?.takeIf { it.isActive }
        }

        // If job exists, await it outside the lock so other callers can also join
        if (existingJob != null) {
            return@coroutineScope existingJob.await()
        }

        // No running job, acquire lock to start new one
        syncMutex.withLock {
            // Double-check after acquiring lock (another thread might have started)
            runningJob?.takeIf { it.isActive }?.let { job ->
                return@withLock job
            }

            // Start new job
            val job = async { performSync() }
            runningJob = job
            job
        }.await()
    }

    private suspend fun performSync(): SyncResult {
        val pendingResponses = repository.getPendingResponses()

        if (pendingResponses.isEmpty()) {
            return SyncResult.empty()
        }

        val succeededIds = mutableListOf<String>()
        val failedIds = mutableListOf<String>()
        val remainingIds = pendingResponses.map { it.id }.toMutableList()
        var consecutiveFailures = 0
        var stopReason: StopReason? = null

        for (response in pendingResponses) {
            remainingIds.remove(response.id)

            val uploadResult = api.upload(response)
            val currentTime = timeProvider.currentTimeMillis()

            if (uploadResult.isSuccess) {
                handleSuccess(response)
                succeededIds.add(response.id)
                consecutiveFailures = 0
            } else {
                val error = uploadResult.getSyncError() ?: SyncError.Unknown()

                handleFailure(response, error, currentTime)
                failedIds.add(response.id)

                if (error.isRetryable()) {
                    consecutiveFailures++
                } else {
                    consecutiveFailures = 0
                }

                if (shouldStopEarly(error, consecutiveFailures)) {
                    stopReason = determineStopReason(error, consecutiveFailures)
                    break
                }
            }
        }

        return SyncResult(
            succeededIds = succeededIds,
            failedIds = failedIds,
            pendingIds = remainingIds,
            stopReason = stopReason
        )
    }

    private suspend fun handleSuccess(response: SurveyResponse) {
        repository.markSynced(response.id)

        if (response.mediaFilePaths.isNotEmpty()) {
            storageManager.deleteFiles(response.mediaFilePaths)
        }
    }

    private suspend fun handleFailure(
        response: SurveyResponse,
        error: SyncError,
        currentTime: Long
    ) {
        val newRetryCount = response.retryCount + 1
        val newStatus = determineFailureStatus(error, newRetryCount)

        repository.updateStatus(
            id = response.id,
            status = newStatus,
            retryCount = newRetryCount,
            lastAttemptAt = currentTime
        )
    }

    private fun determineFailureStatus(error: SyncError, retryCount: Int): SyncStatus {
        return when {
            !error.isRetryable() -> SyncStatus.FAILED_PERMANENT
            retryCount >= config.maxRetryCount -> SyncStatus.FAILED_PERMANENT
            else -> SyncStatus.FAILED_RETRYABLE
        }
    }

    private fun shouldStopEarly(error: SyncError, consecutiveFailures: Int): Boolean {
        return when (error) {
            is SyncError.NoInternet -> true
            is SyncError.Timeout -> consecutiveFailures >= config.consecutiveFailureThreshold
            is SyncError.ServerError -> error.code in 500..599 && consecutiveFailures >= config.consecutiveFailureThreshold
            else -> false
        }
    }

    private fun determineStopReason(error: SyncError, consecutiveFailures: Int): StopReason {
        return when (error) {
            is SyncError.NoInternet -> StopReason.FatalError(error)
            is SyncError.Timeout -> StopReason.NetworkDegradation(consecutiveFailures)
            is SyncError.ServerError -> StopReason.NetworkDegradation(consecutiveFailures)
            else -> StopReason.FatalError(error)
        }
    }

    /**
     * Calculate delay for exponential backoff.
     */
    fun calculateBackoffDelay(retryCount: Int): Long {
        val delay = config.initialBackoffMs * (1 shl minOf(retryCount, config.maxBackoffExponent))
        return minOf(delay, config.maxBackoffMs)
    }
}

/**
 * Configuration for sync engine.
 */
data class SyncConfig(
    val maxRetryCount: Int = 5,
    val consecutiveFailureThreshold: Int = 3,
    val initialBackoffMs: Long = 1000L,
    val maxBackoffMs: Long = 60000L,
    val maxBackoffExponent: Int = 5
)

/**
 * Interface for providing current time. Allows testing with controlled time.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
}

/**
 * Default time provider using system time.
 */
class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
