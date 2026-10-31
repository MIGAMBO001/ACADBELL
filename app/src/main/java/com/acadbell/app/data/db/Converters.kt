package com.acadbell.app.data.db

import androidx.room.TypeConverter
import com.acadbell.app.domain.AlarmKind
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TopicStatus

/** Room type converters. Unknown strings degrade to safe defaults so bad data never crashes the app. */
class Converters {

    @TypeConverter
    fun topicStatusToString(v: TopicStatus): String = v.name

    @TypeConverter
    fun stringToTopicStatus(v: String): TopicStatus =
        runCatching { TopicStatus.valueOf(v) }.getOrDefault(TopicStatus.PENDING)

    @TypeConverter
    fun occurrenceStateToString(v: OccurrenceState): String = v.name

    @TypeConverter
    fun stringToOccurrenceState(v: String): OccurrenceState =
        runCatching { OccurrenceState.valueOf(v) }.getOrDefault(OccurrenceState.SCHEDULED)

    @TypeConverter
    fun runOutcomeToString(v: RunOutcome): String = v.name

    @TypeConverter
    fun stringToRunOutcome(v: String): RunOutcome =
        runCatching { RunOutcome.valueOf(v) }.getOrDefault(RunOutcome.IN_PROGRESS)

    @TypeConverter
    fun alarmKindToString(v: AlarmKind): String = v.name

    @TypeConverter
    fun stringToAlarmKind(v: String): AlarmKind =
        runCatching { AlarmKind.valueOf(v) }.getOrDefault(AlarmKind.START)
}
