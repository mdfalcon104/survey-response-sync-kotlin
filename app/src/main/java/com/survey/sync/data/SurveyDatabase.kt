package com.survey.sync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for survey data.
 */
@Database(
    entities = [SurveyResponseEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SurveyDatabase : RoomDatabase() {

    abstract fun surveyResponseDao(): SurveyResponseDao

    companion object {
        private const val DATABASE_NAME = "survey_database"

        @Volatile
        private var INSTANCE: SurveyDatabase? = null

        fun getInstance(context: Context): SurveyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): SurveyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SurveyDatabase::class.java,
                DATABASE_NAME
            ).build()
        }

        /**
         * Create an in-memory database for testing.
         */
        fun createInMemory(context: Context): SurveyDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SurveyDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
