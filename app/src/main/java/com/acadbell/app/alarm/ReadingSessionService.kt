package com.acadbell.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.AlarmKind
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that owns an in-progress reading session.
 *
 * Why a service and not a ViewModel timer?
 *  • Survives the student switching apps or locking the screen.
 *  • Gives the OS a reason to keep the process alive (foreground notification).
 *  • Writes a checkpoint to Room every 15 s, so even a hard kill loses at most 15 s.
 *  • The END alarm is armed at start time and re-armed on extend, so the session still
 *    ends correctly if this service is killed.
 *
 * Timing model: [accumulatedMillis] counts only *running* time. Pausing stops accumulation;
 * resuming continues it. `actualSeconds` therefore measures real reading, not wall clock.
 */
class ReadingSessionService : LifecycleService() {

    private var runId: Long = -1L
    private var tickerJob: Job? = null
    private var checkpointJob: Job? = null

    /** Exposed only so the Activity can observe manual stop/pause requests. */
    private val binder = android.os.Binder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        _state.value = UiState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_RUN_ID, -1L)
                val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
                if (id > 0) resumeExisting(id) else startNew(occurrenceId)
            }
            ACTION_START_FROM_ALARM -> {
                val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
                startNew(occurrenceId)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopSession(RunOutcome.STOPPED_EARLY, "Stopped by student")
            ACTION_EXTEND -> {
                val minutes = intent.getIntExtra(EXTRA_EXTEND_MINUTES, 5)
                extend(minutes)
            }
            ACTION_FINISH_TIME_UP -> stopSession(RunOutcome.COMPLETED, "Allocated time completed")
            ACTION_SNOOZE_ALARM -> {
                val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                val sound = intent.getStringExtra(EXTRA_SOUND)
                AlarmSoundPlayer.stopPlayback(this)
                ServiceLocator.notifications(this).clearStartAlarm()
                ServiceLocator.notifications(this).clearEndAlarm()
                snoozeAlarm(occurrenceId, minutes, sound)
                stopSelfSafely()
            }
            ACTION_SILENCE -> {
                AlarmSoundPlayer.stopPlayback(this)
                ServiceLocator.notifications(this).clearStartAlarm()
                ServiceLocator.notifications(this).clearEndAlarm()
                stopSelfSafely()
            }
            ACTION_REVIEW_FROM_ALARM -> {
                AlarmSoundPlayer.stopPlayback(this)
                ServiceLocator.notifications(this).clearEndAlarm()
                stopSelfSafely()
            }
            ACTION_FINISH_FROM_ALARM -> {
                val id = intent.getLongExtra(EXTRA_RUN_ID, -1L)
                if (id > 0) finishFromAlarmInternal(id)
            }
            else -> Unit
        }
        return START_STICKY
    }

    // ───────────────────────── start / resume ─────────────────────────

    private fun startNew(occurrenceId: Long?) {
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            // Guard: never run two sessions at once.
            val existing = repo.getActiveRun()
            if (existing != null) {
                runId = existing.id
                resumeExisting(existing.id)
                return@launch
            }
            runId = repo.startRun(if (occurrenceId != null && occurrenceId > 0) occurrenceId else null)
            repo.getRun(runId)?.let { beginTracking(it) }
            if (occurrenceId != null && occurrenceId > 0) {
                armEndAlarm(occurrenceId)
            }
        }
    }

    private fun resumeExisting(id: Long) {
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            val run = repo.getRun(id) ?: return@launch
            runId = run.id
            beginTracking(run)
            run.occurrenceId?.let { armEndAlarm(it, run) }
        }
    }

    private suspend fun armEndAlarm(occurrenceId: Long, run: SessionRunEntity? = null) {
        val repo = ServiceLocator.repository(this)
        val scheduler = ServiceLocator.alarmScheduler(this)
        val occurrence = repo.getOccurrence(occurrenceId)
        if (occurrence != null) {
            scheduler.armEnd(occurrence)
        }
    }

    private fun beginTracking(run: SessionRunEntity) {
        _state.value = UiState(
            runId = run.id,
            title = run.sessionTitle,
            courseLabel = if (run.courseCode.isNotBlank()) "${run.courseCode} · ${run.courseTitle}" else run.courseTitle,
            allocatedSeconds = run.allocatedSeconds,
            accumulatedSeconds = run.actualSeconds,
            pausedSeconds = run.pausedSeconds,
            extendedSeconds = run.extendedSeconds,
            isRunning = true,
            startedAt = run.startedAt
        )
        startForegroundWithNotification()
        startTicker()
        startCheckpointing()
    }

    private fun startForegroundWithNotification() {
        val state = _state.value
        val notification = ServiceLocator.notifications(this).sessionForegroundNotification(
            runId = state.runId,
            title = state.title,
            courseLabel = state.courseLabel,
            remainingLabel = TimeUtils.formatDurationSeconds(state.remainingSeconds),
            paused = !state.isRunning
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
        }
    }

    // ───────────────────────── ticker ─────────────────────────

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                val current = _state.value
                if (!current.isRunning) continue
                val next = current.accumulatedSeconds + 1
                _state.value = current.copy(accumulatedSeconds = next)

                // Mirror the countdown in the notification once a minute (cheap, no flicker).
                if (next % 60L == 0L) startForegroundWithNotification()

                // Local safety net: if the system alarm was suppressed, end the session anyway.
                if (next >= current.allocatedSeconds) {
                    _state.value = _state.value.copy(reachedZeroAt = System.currentTimeMillis())
                    stopSession(RunOutcome.COMPLETED, "Allocated time completed")
                    return@launch
                }
            }
        }
    }

    private fun startCheckpointing() {
        checkpointJob?.cancel()
        checkpointJob = lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            while (true) {
                delay(15_000)
                val s = _state.value
                if (s.runId > 0 && s.isRunning) {
                    repo.checkpointRun(s.runId, s.accumulatedSeconds, s.pausedSeconds)
                }
            }
        }
    }

    // ───────────────────────── controls ─────────────────────────

    fun pause() {
        val s = _state.value
        if (!s.isRunning || s.runId <= 0) return
        _state.value = s.copy(isRunning = false)
        startForegroundWithNotification()
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            repo.checkpointRun(s.runId, s.accumulatedSeconds, s.pausedSeconds)
            repo.log("RUN_PAUSED", "run=${s.runId} at ${s.accumulatedSeconds}s")
        }
    }

    fun resume() {
        val s = _state.value
        if (s.isRunning || s.runId <= 0) return
        _state.value = s.copy(isRunning = true)
        startForegroundWithNotification()
        lifecycleScope.launch {
            ServiceLocator.repository(this@ReadingSessionService).log("RUN_RESUMED", "run=${s.runId}")
        }
    }

    /** Adds reading time to the allocation and re-arms the END alarm past the new finish line. */
    fun extend(minutes: Int) {
        val s = _state.value
        if (s.runId <= 0) return
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            val extra = minutes * 60L
            repo.extendRun(s.runId, extra)
            _state.value = _state.value.copy(
                allocatedSeconds = _state.value.allocatedSeconds + extra,
                extendedSeconds = _state.value.extendedSeconds + extra
            )
            val run = repo.getRun(s.runId)
            val occurrenceId = run?.occurrenceId
            if (occurrenceId != null) {
                val scheduler = ServiceLocator.alarmScheduler(this@ReadingSessionService)
                // Cancel the stale END alarm that would fire at the OLD time, then re-arm.
                scheduler.cancel(occurrenceId, AlarmKind.END)
                val newEnd = System.currentTimeMillis() + (_state.value.remainingSeconds * 1000)
                scheduler.armAdHocEnd(occurrenceId, newEnd)
            }
            repo.log("RUN_EXTENDED", "run=${s.runId} +${minutes}m")
        }
    }

    fun stopSession(outcome: RunOutcome, note: String) {
        val s = _state.value
        if (s.runId <= 0) {
            stopSelfSafely()
            return
        }
        tickerJob?.cancel()
        checkpointJob?.cancel()
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            repo.checkpointRun(s.runId, s.accumulatedSeconds, s.pausedSeconds)
            repo.finishRun(s.runId, outcome, note)
            val run = repo.getRun(s.runId)
            run?.occurrenceId?.let { occId ->
                ServiceLocator.alarmScheduler(this@ReadingSessionService).cancel(occId, AlarmKind.END)
                repo.cancelAlarmsForOccurrence(occId)
            }
            AlarmSoundPlayer.stopPlayback(this@ReadingSessionService)
            _state.value = _state.value.copy(isRunning = false, isFinished = true, outcome = outcome.name)
            stopSelfSafely()
        }
    }

    private fun finishFromAlarmInternal(id: Long) {
        lifecycleScope.launch {
            val repo = ServiceLocator.repository(this@ReadingSessionService)
            val run = repo.getRun(id) ?: return@launch
            val state = _state.value
            val actual = if (state.runId == id) state.accumulatedSeconds else run.actualSeconds
            repo.checkpointRun(id, actual, run.pausedSeconds)
            repo.finishRun(id, RunOutcome.COMPLETED, "Ended by end alarm")
            AlarmSoundPlayer.stopPlayback(this@ReadingSessionService)
            _state.value = _state.value.copy(isRunning = false, isFinished = true, outcome = RunOutcome.COMPLETED.name)
            stopSelfSafely()
        }
    }

    private fun snoozeAlarm(occurrenceId: Long, minutes: Int, sound: String?) {
        val target = System.currentTimeMillis() + minutes * 60_000L
        ServiceLocator.alarmScheduler(this).armSnooze(occurrenceId, target)
        lifecycleScope.launch {
            ServiceLocator.repository(this@ReadingSessionService)
                .log("ALARM_SNOOZED", "occurrence=$occurrenceId for ${minutes}m")
        }
        ServiceLocator.notifications(this).showSimple(
            "Alarm snoozed",
            "ACADBELL will ring again in $minutes minute(s)."
        )
    }

    private fun stopSelfSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        checkpointJob?.cancel()
        super.onDestroy()
    }

    /** A snapshot the UI and the notification both read. */
    data class UiState(
        val runId: Long = -1L,
        val title: String = "",
        val courseLabel: String = "",
        val allocatedSeconds: Long = 0,
        val accumulatedSeconds: Long = 0,
        val pausedSeconds: Long = 0,
        val extendedSeconds: Long = 0,
        val isRunning: Boolean = false,
        val isFinished: Boolean = false,
        val outcome: String = "",
        val startedAt: Long = 0,
        val reachedZeroAt: Long? = null
    ) {
        val remainingSeconds: Long get() = (allocatedSeconds - accumulatedSeconds).coerceAtLeast(0)
        val progressFraction: Float
            get() = if (allocatedSeconds <= 0) 0f
            else (accumulatedSeconds.toFloat() / allocatedSeconds.toFloat()).coerceIn(0f, 1f)
        val isOvertime: Boolean get() = accumulatedSeconds > allocatedSeconds
    }

    companion object {
        const val NOTIF_ID = 4242

        const val ACTION_START = "com.acadbell.app.session.START"
        const val ACTION_START_FROM_ALARM = "com.acadbell.app.session.START_FROM_ALARM"
        const val ACTION_PAUSE = "com.acadbell.app.session.PAUSE"
        const val ACTION_RESUME = "com.acadbell.app.session.RESUME"
        const val ACTION_STOP = "com.acadbell.app.session.STOP"
        const val ACTION_EXTEND = "com.acadbell.app.session.EXTEND"
        const val ACTION_FINISH_TIME_UP = "com.acadbell.app.session.FINISH_TIME_UP"
        const val ACTION_FINISH_FROM_ALARM = "com.acadbell.app.session.FINISH_FROM_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.acadbell.app.session.SNOOZE_ALARM"
        const val ACTION_SILENCE = "com.acadbell.app.session.SILENCE"
        const val ACTION_REVIEW_FROM_ALARM = "com.acadbell.app.session.REVIEW_FROM_ALARM"

        const val EXTRA_RUN_ID = "extra_run_id"
        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
        const val EXTRA_EXTEND_MINUTES = "extra_extend_minutes"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_SOUND = "extra_sound"

        /** Live state, readable from the UI without binding to the service. */
        private val _state = MutableStateFlow(UiState())
        val state: StateFlow<UiState> = _state.asStateFlow()

        // ── Static helpers so UI / receivers can drive the service ──

        fun start(context: Context, occurrenceId: Long? = null, runId: Long? = null) {
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                action = ACTION_START
                occurrenceId?.let { putExtra(EXTRA_OCCURRENCE_ID, it) }
                runId?.let { putExtra(EXTRA_RUN_ID, it) }
            }
            startCompat(context, intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun extend(context: Context, minutes: Int) =
            send(context, ACTION_EXTEND) { it.putExtra(EXTRA_EXTEND_MINUTES, minutes) }

        fun finishFromAlarm(context: Context, runId: Long) {
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                action = ACTION_FINISH_FROM_ALARM
                putExtra(EXTRA_RUN_ID, runId)
            }
            startCompat(context, intent)
        }

        private fun send(context: Context, action: String, block: ((Intent) -> Unit)? = null) {
            val intent = Intent(context, ReadingSessionService::class.java).apply {
                this.action = action
                block?.invoke(this)
            }
            startCompat(context, intent)
        }

        private fun startCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
