package com.survey.sync.domain

import com.survey.sync.core.SyncError

/**
 * API interface for uploading survey responses.
 */
interface SurveyApi {

    /**
     * Upload a survey response to the server.
     *
     * @param response The survey response to upload
     * @return Result.success(Unit) on success, Result.failure with SyncError on failure
     */
    suspend fun upload(response: SurveyResponse): Result<Unit>
}

/**
 * Extension to extract SyncError from a failed result.
 */
fun <T> Result<T>.getSyncError(): SyncError? {
    return exceptionOrNull()?.let { throwable ->
        when (throwable) {
            is SyncException -> throwable.error
            else -> SyncError.Unknown(throwable)
        }
    }
}

/**
 * Exception wrapper for SyncError to use with Result.
 */
class SyncException(val error: SyncError) : Exception(error.toString())
