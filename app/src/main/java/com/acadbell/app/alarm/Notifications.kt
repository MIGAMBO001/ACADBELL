package com.acadbell.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.acadbell.app.MainActivity
import com.acadbell.app.R

/**
 * All notification construction. Three channels keep the student in control:
 *  • alarms   (high importance, sound + vibrate, full-screen style)
 *  • session  (low importance, silent, ongoing timer mirror)
 *  • reminders(weekly summary / maintenance notices)
 */
class Notifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Reading alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Start and end alarms for your reading sessions"
                enableVibration(true)
                setSound(null, null) // sound is played by AlarmSoundPlayer so it can be looped/stopped
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Active session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing reading session timer"
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders & summaries", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Weekly progress and reliability notices"
            }
        )
    }

    private fun openAppIntent(route: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra("acadbell.route", it) }
        }
        return PendingIntent.getActivity(
            context,
            route?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun action(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ───────────────────────── START alarm ─────────────────────────

    fun showStartAlarm(
        occurrenceId: Long,
        sessionId: Long,
        title: String,
        courseLabel: String,
        topicCount: Int,
        durationMinutes: Int,
        sound: String,
        vibrate: Boolean,
        snoozeMinutes: Int
    ) {
        AlarmSoundPlayer.play(context, sound, vibrate, loop = false)

        val startNow = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_START_FROM_ALARM
            putExtra(ReadingSessionService.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val snooze = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_SNOOZE_ALARM
            putExtra(ReadingSessionService.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(ReadingSessionService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(ReadingSessionService.EXTRA_SOUND, sound)
        }
        val dismiss = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_SILENCE
        }

        val body = buildString {
            append(courseLabel)
            append(" · ")
            append(durationMinutes)
            append(" min")
            if (topicCount > 0) append(" · $topicCount topic(s)")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Time to read: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\nReading starts now. Tap to open ACADBELL."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openAppIntent("session/$occurrenceId"), true)
            .setContentIntent(openAppIntent("session/$occurrenceId"))
            .addAction(R.drawable.ic_play, "Start reading", action(startNow, 101))
            .addAction(R.drawable.ic_snooze, "Snooze ${snoozeMinutes}m", action(snooze, 102))
            .addAction(R.drawable.ic_stop, "Dismiss", action(dismiss, 103))
            .build()

        notifySafely(NOTIF_START, notification)
    }

    // ───────────────────────── END alarm ─────────────────────────

    fun showEndAlarm(
        occurrenceId: Long,
        title: String,
        courseLabel: String,
        sound: String,
        vibrate: Boolean,
        snoozeMinutes: Int
    ) {
        AlarmSoundPlayer.play(context, sound, vibrate, loop = false)

        val review = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_REVIEW_FROM_ALARM
            putExtra(ReadingSessionService.EXTRA_OCCURRENCE_ID, occurrenceId)
        }
        val snooze = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_SNOOZE_ALARM
            putExtra(ReadingSessionService.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(ReadingSessionService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(ReadingSessionService.EXTRA_SOUND, sound)
        }
        val stop = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_SILENCE
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Reading time is up")
            .setContentText("$title · $courseLabel")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title · $courseLabel\nYour allocated time has ended. Review the topics you covered — nothing was marked complete for you.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setFullScreenIntent(openAppIntent("summary/$occurrenceId"), true)
            .setContentIntent(openAppIntent("summary/$occurrenceId"))
            .addAction(R.drawable.ic_check, "Review topics", action(review, 201))
            .addAction(R.drawable.ic_snooze, "Snooze ${snoozeMinutes}m", action(snooze, 202))
            .addAction(R.drawable.ic_stop, "Stop", action(stop, 203))
            .build()

        notifySafely(NOTIF_END, notification)
    }

    // ───────────────────── Ongoing session mirror ─────────────────────

    fun sessionForegroundNotification(
        runId: Long,
        title: String,
        courseLabel: String,
        remainingLabel: String,
        paused: Boolean
    ): Notification {
        val pauseResume = Intent(context, ReadingSessionService::class.java).apply {
            action = if (paused) ReadingSessionService.ACTION_RESUME else ReadingSessionService.ACTION_PAUSE
        }
        val stop = Intent(context, ReadingSessionService::class.java).apply {
            action = ReadingSessionService.ACTION_STOP
        }
        return NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(if (paused) "Paused · $title" else "$remainingLabel left · $title")
            .setContentText(courseLabel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent("active/$runId"))
            .addAction(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                if (paused) "Resume" else "Pause",
                action(pauseResume, 301)
            )
            .addAction(R.drawable.ic_stop, "Stop", action(stop, 302))
            .build()
    }

    fun clearStartAlarm() = manager.cancel(NOTIF_START)
    fun clearEndAlarm() = manager.cancel(NOTIF_END)

    fun showSimple(title: String, body: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notifySafely(NOTIF_REMINDER, n)
    }

    fun canPostNotifications(): Boolean = manager.areNotificationsEnabled()

    private fun notifySafely(id: Int, notification: Notification) {
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CHANNEL_ALARMS = "acadbell_alarms"
        const val CHANNEL_SESSION = "acadbell_session"
        const val CHANNEL_REMINDERS = "acadbell_reminders"

        const val NOTIF_START = 1001
        const val NOTIF_END = 1002
        const val NOTIF_SESSION = 1003
        const val NOTIF_REMINDER = 1004
    }
}
