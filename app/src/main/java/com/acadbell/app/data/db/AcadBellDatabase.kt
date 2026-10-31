package com.acadbell.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CourseEntity::class,
        TopicEntity::class,
        ReadingSessionEntity::class,
        SessionTopicCrossRef::class,
        SessionOccurrenceEntity::class,
        SessionRunEntity::class,
        AlarmRecordEntity::class,
        AppEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AcadBellDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun topicDao(): TopicDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun sessionRunDao(): SessionRunDao
    abstract fun alarmDao(): AlarmDao
    abstract fun appEventDao(): AppEventDao

    companion object {
        private const val DB_NAME = "acadbell.db"

        @Volatile
        private var instance: AcadBellDatabase? = null

        fun get(context: Context): AcadBellDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AcadBellDatabase::class.java,
                    DB_NAME
                )
                    // Offline-first: if a future schema migration is ever missed, keep the
                    // student's data rather than crashing the app.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
