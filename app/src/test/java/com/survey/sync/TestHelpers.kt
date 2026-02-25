package com.survey.sync

import com.survey.sync.core.SyncStatus
import com.survey.sync.domain.SurveyResponse
import java.util.UUID

/**
 * Helper functions for creating test data.
 */
object TestHelpers {

    /**
     * Example JSON for a survey with repeating sections.
     * The farmer has 2 farms, each with its own set of questions.
     */
    val REPEATING_SECTIONS_JSON = """
        {
            "farmerName": "John Doe",
            "totalFarms": 2,
            "farms": [
                {
                    "farmIndex": 1,
                    "cropType": "maize",
                    "areaHectares": 2.5,
                    "yieldEstimate": 1500
                },
                {
                    "farmIndex": 2,
                    "cropType": "wheat",
                    "areaHectares": 1.8,
                    "yieldEstimate": 900
                }
            ]
        }
    """.trimIndent()

    /**
     * Create a survey response with default values.
     */
    fun createSurveyResponse(
        id: String = UUID.randomUUID().toString(),
        farmerId: String = "farmer-001",
        createdAt: Long = System.currentTimeMillis(),
        answersJson: String = """{"q1": "answer1", "q2": "answer2"}""",
        status: SyncStatus = SyncStatus.PENDING,
        retryCount: Int = 0,
        lastAttemptAt: Long? = null,
        mediaFilePaths: List<String> = emptyList()
    ): SurveyResponse = SurveyResponse(
        id = id,
        farmerId = farmerId,
        createdAt = createdAt,
        answersJson = answersJson,
        status = status,
        retryCount = retryCount,
        lastAttemptAt = lastAttemptAt,
        mediaFilePaths = mediaFilePaths
    )

    /**
     * Create multiple survey responses.
     */
    fun createSurveyResponses(
        count: Int,
        baseId: String = "response",
        farmerId: String = "farmer-001",
        status: SyncStatus = SyncStatus.PENDING
    ): List<SurveyResponse> {
        return (1..count).map { i ->
            createSurveyResponse(
                id = "$baseId-$i",
                farmerId = farmerId,
                createdAt = 1000L + i,
                status = status
            )
        }
    }

    /**
     * Create a survey response with repeating sections.
     */
    fun createSurveyResponseWithRepeatingSections(
        id: String = UUID.randomUUID().toString(),
        farmerId: String = "farmer-001"
    ): SurveyResponse = createSurveyResponse(
        id = id,
        farmerId = farmerId,
        answersJson = REPEATING_SECTIONS_JSON
    )
}
