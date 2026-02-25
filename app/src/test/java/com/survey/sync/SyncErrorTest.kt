package com.survey.sync

import com.google.common.truth.Truth.assertThat
import com.survey.sync.core.SyncError
import org.junit.Test

/**
 * Tests for SyncError classification.
 */
class SyncErrorTest {

    @Test
    fun `NoInternet is retryable`() {
        assertThat(SyncError.NoInternet.isRetryable()).isTrue()
    }

    @Test
    fun `Timeout is retryable`() {
        assertThat(SyncError.Timeout.isRetryable()).isTrue()
    }

    @Test
    fun `ServerError 500 is retryable`() {
        assertThat(SyncError.ServerError(500).isRetryable()).isTrue()
    }

    @Test
    fun `ServerError 502 is retryable`() {
        assertThat(SyncError.ServerError(502).isRetryable()).isTrue()
    }

    @Test
    fun `ServerError 503 is retryable`() {
        assertThat(SyncError.ServerError(503).isRetryable()).isTrue()
    }

    @Test
    fun `ServerError 400 is not retryable`() {
        assertThat(SyncError.ServerError(400).isRetryable()).isFalse()
    }

    @Test
    fun `ServerError 401 is not retryable`() {
        assertThat(SyncError.ServerError(401).isRetryable()).isFalse()
    }

    @Test
    fun `ServerError 404 is not retryable`() {
        assertThat(SyncError.ServerError(404).isRetryable()).isFalse()
    }

    @Test
    fun `ServerError 422 is not retryable`() {
        assertThat(SyncError.ServerError(422).isRetryable()).isFalse()
    }

    @Test
    fun `Serialization error is not retryable`() {
        assertThat(SyncError.Serialization().isRetryable()).isFalse()
    }

    @Test
    fun `Unknown error is not retryable`() {
        assertThat(SyncError.Unknown().isRetryable()).isFalse()
    }
}
