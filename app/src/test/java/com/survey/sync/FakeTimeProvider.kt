package com.survey.sync

import com.survey.sync.domain.TimeProvider

/**
 * Fake time provider for testing.
 * Allows controlling time in tests.
 */
class FakeTimeProvider(
    private var currentTime: Long = 1000000L
) : TimeProvider {

    override fun currentTimeMillis(): Long = currentTime

    /**
     * Set the current time.
     */
    fun setTime(time: Long) {
        currentTime = time
    }

    /**
     * Advance time by the given amount.
     */
    fun advanceBy(millis: Long) {
        currentTime += millis
    }
}
