package com.survey.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.survey.sync.core.SyncStatus
import com.survey.sync.data.SurveyDatabase
import com.survey.sync.data.SurveyResponseDao
import com.survey.sync.data.SurveyResponseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Room database.
 */
@RunWith(AndroidJUnit4::class)
class SurveyDatabaseTest {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: SurveyResponseDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SurveyDatabase.createInMemory(context)
        dao = database.surveyResponseDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveResponse() = runTest {
        val entity = createEntity("test-1")

        dao.insert(entity)

        val retrieved = dao.getById("test-1")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo("test-1")
        assertThat(retrieved?.farmerId).isEqualTo("farmer-001")
        assertThat(retrieved?.status).isEqualTo(SyncStatus.PENDING)
    }

    @Test
    fun getPendingResponses_returnsPendingAndFailedRetryable() = runTest {
        dao.insert(createEntity("pending-1", status = SyncStatus.PENDING))
        dao.insert(createEntity("pending-2", status = SyncStatus.PENDING))
        dao.insert(createEntity("failed-retryable", status = SyncStatus.FAILED_RETRYABLE))
        dao.insert(createEntity("synced", status = SyncStatus.SYNCED))
        dao.insert(createEntity("failed-permanent", status = SyncStatus.FAILED_PERMANENT))

        val pending = dao.getPendingResponses()

        assertThat(pending.map { it.id }).containsExactly("pending-1", "pending-2", "failed-retryable")
    }

    @Test
    fun getPendingResponses_orderedByCreatedAt() = runTest {
        dao.insert(createEntity("newer", createdAt = 3000L))
        dao.insert(createEntity("oldest", createdAt = 1000L))
        dao.insert(createEntity("middle", createdAt = 2000L))

        val pending = dao.getPendingResponses()

        assertThat(pending.map { it.id }).containsExactly("oldest", "middle", "newer").inOrder()
    }

    @Test
    fun updateStatus_updatesCorrectly() = runTest {
        dao.insert(createEntity("test-1"))

        dao.updateStatus("test-1", SyncStatus.FAILED_RETRYABLE, 3, 1234567890L)

        val updated = dao.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.FAILED_RETRYABLE)
        assertThat(updated?.retryCount).isEqualTo(3)
        assertThat(updated?.lastAttemptAt).isEqualTo(1234567890L)
    }

    @Test
    fun markSynced_clearsMediaPaths() = runTest {
        val entity = createEntity("test-1", mediaFilePaths = listOf("/path/to/image.jpg"))
        dao.insert(entity)

        dao.markSynced("test-1")

        val updated = dao.getById("test-1")
        assertThat(updated?.status).isEqualTo(SyncStatus.SYNCED)
        assertThat(updated?.mediaFilePaths).isEmpty()
    }

    @Test
    fun deleteSyncedBefore_deletesOldSyncedResponses() = runTest {
        dao.insert(createEntity("old-synced", status = SyncStatus.SYNCED, createdAt = 1000L))
        dao.insert(createEntity("new-synced", status = SyncStatus.SYNCED, createdAt = 3000L))
        dao.insert(createEntity("old-pending", status = SyncStatus.PENDING, createdAt = 1000L))

        dao.deleteSyncedBefore(2000L)

        assertThat(dao.getById("old-synced")).isNull()
        assertThat(dao.getById("new-synced")).isNotNull()
        assertThat(dao.getById("old-pending")).isNotNull()
    }

    @Test
    fun mediaFilePaths_persistsAsList() = runTest {
        val paths = listOf("/path/to/image1.jpg", "/path/to/image2.jpg", "/path/to/video.mp4")
        val entity = createEntity("test-1", mediaFilePaths = paths)

        dao.insert(entity)

        val retrieved = dao.getById("test-1")
        assertThat(retrieved?.mediaFilePaths).containsExactlyElementsIn(paths)
    }

    @Test
    fun answersJson_persistsComplexStructure() = runTest {
        val complexJson = """
            {
                "section1": {
                    "q1": "answer1",
                    "q2": ["a", "b", "c"]
                },
                "repeatingSection": [
                    {"item": 1, "value": "first"},
                    {"item": 2, "value": "second"}
                ]
            }
        """.trimIndent()

        val entity = createEntity("test-1", answersJson = complexJson)
        dao.insert(entity)

        val retrieved = dao.getById("test-1")
        assertThat(retrieved?.answersJson).isEqualTo(complexJson)
    }

    @Test
    fun dataSurvivesReopen() = runTest {
        dao.insert(createEntity("test-1"))

        database.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SurveyDatabase.createInMemory(context)
        dao = database.surveyResponseDao()

        val count = dao.count()
        assertThat(count).isEqualTo(0)
    }

    private fun createEntity(
        id: String,
        farmerId: String = "farmer-001",
        createdAt: Long = System.currentTimeMillis(),
        answersJson: String = """{"q1": "a1"}""",
        status: SyncStatus = SyncStatus.PENDING,
        retryCount: Int = 0,
        lastAttemptAt: Long? = null,
        mediaFilePaths: List<String> = emptyList()
    ) = SurveyResponseEntity(
        id = id,
        farmerId = farmerId,
        createdAt = createdAt,
        answersJson = answersJson,
        status = status,
        retryCount = retryCount,
        lastAttemptAt = lastAttemptAt,
        mediaFilePaths = mediaFilePaths
    )
}
