package com.survey.sync

import com.survey.sync.core.SyncError
import com.survey.sync.domain.SurveyApi
import com.survey.sync.domain.SurveyResponse
import com.survey.sync.domain.SyncException
import kotlinx.coroutines.delay

/**
 * Configurable fake API for testing sync scenarios.
 *
 * Supports various failure modes:
 * - Always success
 * - Fail on specific call number
 * - Always timeout
 * - Always server error (400 or 500)
 */
class FakeSurveyApi : SurveyApi {

    private var callCount = 0
    private var behavior: ApiBehavior = ApiBehavior.AlwaysSuccess
    private val uploadedResponses = mutableListOf<SurveyResponse>()

    /**
     * Configure API behavior.
     */
    fun setBehavior(behavior: ApiBehavior) {
        this.behavior = behavior
    }

    /**
     * Reset state for new test.
     */
    fun reset() {
        callCount = 0
        uploadedResponses.clear()
        behavior = ApiBehavior.AlwaysSuccess
    }

    /**
     * Get all responses that were successfully uploaded.
     */
    fun getUploadedResponses(): List<SurveyResponse> = uploadedResponses.toList()

    /**
     * Get total number of upload attempts.
     */
    fun getCallCount(): Int = callCount

    override suspend fun upload(response: SurveyResponse): Result<Unit> {
        callCount++

        return when (val b = behavior) {
            is ApiBehavior.AlwaysSuccess -> {
                uploadedResponses.add(response)
                Result.success(Unit)
            }

            is ApiBehavior.AlwaysFail -> {
                Result.failure(SyncException(b.error))
            }

            is ApiBehavior.FailOnCall -> {
                if (callCount in b.failOnCalls) {
                    Result.failure(SyncException(b.error))
                } else {
                    uploadedResponses.add(response)
                    Result.success(Unit)
                }
            }

            is ApiBehavior.FailAfterCall -> {
                if (callCount > b.succeedUntil) {
                    Result.failure(SyncException(b.error))
                } else {
                    uploadedResponses.add(response)
                    Result.success(Unit)
                }
            }

            is ApiBehavior.Timeout -> {
                delay(b.delayMs)
                Result.failure(SyncException(SyncError.Timeout))
            }

            is ApiBehavior.ServerError400 -> {
                Result.failure(SyncException(SyncError.ServerError(400, "Bad Request")))
            }

            is ApiBehavior.ServerError500 -> {
                Result.failure(SyncException(SyncError.ServerError(500, "Internal Server Error")))
            }

            is ApiBehavior.NoInternet -> {
                Result.failure(SyncException(SyncError.NoInternet))
            }

            is ApiBehavior.Custom -> {
                b.handler(callCount, response)
            }
        }
    }

    /**
     * Different behaviors for the fake API.
     */
    sealed class ApiBehavior {
        data object AlwaysSuccess : ApiBehavior()

        data class AlwaysFail(val error: SyncError) : ApiBehavior()

        data class FailOnCall(
            val failOnCalls: Set<Int>,
            val error: SyncError = SyncError.ServerError(500)
        ) : ApiBehavior()

        data class FailAfterCall(
            val succeedUntil: Int,
            val error: SyncError = SyncError.ServerError(500)
        ) : ApiBehavior()

        data class Timeout(val delayMs: Long = 5000) : ApiBehavior()

        data object ServerError400 : ApiBehavior()

        data object ServerError500 : ApiBehavior()

        data object NoInternet : ApiBehavior()

        data class Custom(
            val handler: suspend (callNumber: Int, response: SurveyResponse) -> Result<Unit>
        ) : ApiBehavior()
    }
}
