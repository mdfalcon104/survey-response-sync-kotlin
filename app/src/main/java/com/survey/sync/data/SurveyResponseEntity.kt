package com.survey.sync.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.survey.sync.core.SyncStatus
import com.survey.sync.domain.SurveyResponse

/**
 * Room entity for survey response storage.
 */
@Entity(tableName = "survey_responses")
@TypeConverters(Converters::class)
data class SurveyResponseEntity(
    @PrimaryKey
    val id: String,
    val farmerId: String,
    val createdAt: Long,
    val answersJson: String,
    val status: SyncStatus,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val mediaFilePaths: List<String>
) {
    fun toDomain(): SurveyResponse = SurveyResponse(
        id = id,
        farmerId = farmerId,
        createdAt = createdAt,
        answersJson = answersJson,
        status = status,
        retryCount = retryCount,
        lastAttemptAt = lastAttemptAt,
        mediaFilePaths = mediaFilePaths
    )

    companion object {
        fun fromDomain(response: SurveyResponse): SurveyResponseEntity = SurveyResponseEntity(
            id = response.id,
            farmerId = response.farmerId,
            createdAt = response.createdAt,
            answersJson = response.answersJson,
            status = response.status,
            retryCount = response.retryCount,
            lastAttemptAt = response.lastAttemptAt,
            mediaFilePaths = response.mediaFilePaths
        )
    }
}

/**
 * Type converters for Room.
 */
class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
