package com.acadbell.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.acadbell.app.domain.AlarmSoundChoice
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays alarm tones without shipping audio assets and without any network dependency.
 *
 * Custom sounds are synthesised into a small PCM buffer and played through MediaPlayer.
 * Where synthesis is unavailable we fall back to the system alarm ringtone, so a student is
 * never left with a silent alarm.
 */
object AlarmSoundPlayer {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun play(context: Context, soundName: String, vibrate: Boolean, loop: Boolean = false) {
        stopPlayback(context)
        val choice = runCatching { AlarmSoundChoice.valueOf(soundName) }
            .getOrDefault(AlarmSoundChoice.CLASSIC_BELL)

        if (choice != AlarmSoundChoice.SILENT) {
            runCatching {
                val uri = uriFor(context, choice)
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    isLooping = loop
                    prepare()
                    start()
                }
            }.onFailure {
                playSystemAlarm(context, loop)
            }
        }
        if (vibrate) startVibration(context, loop)
    }

    fun ensurePlaying(context: Context, soundName: String, vibrate: Boolean) {
        if (player?.isPlaying == true) return
        play(context, soundName, vibrate, loop = true)
    }

    fun stopPlayback(context: Context) {
        runCatching {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun playSystemAlarm(context: Context, loop: Boolean) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = loop
                prepare()
                start()
            }
        }
    }

    /**
     * Resolves a playable URI for the chosen sound.
     * Synthesised tones are written once to the app cache directory (offline, tiny).
     */
    private fun uriFor(context: Context, choice: AlarmSoundChoice): Uri {
        val file = ToneSynthesis.fileFor(context, choice)
        return Uri.fromFile(file)
    }

    private fun startVibration(context: Context, repeating: Boolean) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 500, 300, 500, 300, 800)
        vibrator?.let { v ->
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, if (repeating) 0 else -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, if (repeating) 0 else -1)
            }
        }
    }

    /**
     * Generates short WAV tones (16-bit mono PCM). Deterministic and dependency-free,
     * so the app stays fully offline and small.
     */
    object ToneSynthesis {
        private const val SAMPLE_RATE = 22050

        fun fileFor(context: Context, choice: AlarmSoundChoice): java.io.File {
            val dir = java.io.File(context.cacheDir, "tones").apply { mkdirs() }
            val file = java.io.File(dir, "${choice.name.lowercase()}.wav")
            if (file.exists() && file.length() > 1000) return file
            val samples = buildSamples(choice)
            writeWav(file, samples)
            return file
        }

        private fun buildSamples(choice: AlarmSoundChoice): ShortArray {
            val spec: Pair<Double, Double> = when (choice) {
                AlarmSoundChoice.CLASSIC_BELL -> 880.0 to 1.6
                AlarmSoundChoice.DIGITAL -> 1320.0 to 1.2
                AlarmSoundChoice.CHIME -> 660.0 to 2.4
                AlarmSoundChoice.GENTLE -> 440.0 to 3.0
                AlarmSoundChoice.SILENT -> 0.0 to 0.2
            }
            val (freq, seconds) = spec
            val total = (SAMPLE_RATE * seconds).toInt()
            val out = ShortArray(total)
            if (freq == 0.0) return out

            val repeats = when (choice) {
                AlarmSoundChoice.CLASSIC_BELL -> 3
                AlarmSoundChoice.DIGITAL -> 5
                AlarmSoundChoice.CHIME -> 2
                AlarmSoundChoice.GENTLE -> 1
                else -> 1
            }
            val period = total / repeats

            for (i in 0 until total) {
                val localT = (i % period).toDouble() / SAMPLE_RATE
                val progress = localT / (period.toDouble() / SAMPLE_RATE)
                // Exponential decay envelope; gentle choice fades in instead.
                val env = when (choice) {
                    AlarmSoundChoice.GENTLE -> (progress.coerceIn(0.0, 1.0)) * (1 - progress * 0.6)
                    else -> kotlin.math.exp(-3.2 * progress)
                }
                val t = i.toDouble() / SAMPLE_RATE
                val wave = when (choice) {
                    AlarmSoundChoice.DIGITAL -> if (sin(2 * PI * freq * t) >= 0) 0.55 else -0.55
                    AlarmSoundChoice.CHIME -> 0.6 * sin(2 * PI * freq * t) + 0.25 * sin(2 * PI * freq * 2.5 * t)
                    AlarmSoundChoice.CLASSIC_BELL -> 0.5 * sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 1.5 * t)
                    else -> 0.7 * sin(2 * PI * freq * t)
                }
                out[i] = (wave * env * Short.MAX_VALUE * 0.85).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return out
        }

        private fun writeWav(file: java.io.File, samples: ShortArray) {
            runCatching {
                java.io.DataOutputStream(java.io.BufferedOutputStream(file.outputStream())).use { out ->
                    val dataSize = samples.size * 2
                    val byteRate = SAMPLE_RATE * 2
                    out.writeBytes("RIFF")
                    out.writeIntLe(36 + dataSize)
                    out.writeBytes("WAVE")
                    out.writeBytes("fmt ")
                    out.writeIntLe(16)
                    out.writeShortLe(1)            // PCM
                    out.writeShortLe(1)            // mono
                    out.writeIntLe(SAMPLE_RATE)
                    out.writeIntLe(byteRate)
                    out.writeShortLe(2)            // block align
                    out.writeShortLe(16)           // bits per sample
                    out.writeBytes("data")
                    out.writeIntLe(dataSize)
                    samples.forEach { out.writeShortLe(it.toInt()) }
                }
            }
        }

        private fun java.io.DataOutputStream.writeIntLe(value: Int) {
            writeByte(value and 0xFF)
            writeByte((value shr 8) and 0xFF)
            writeByte((value shr 16) and 0xFF)
            writeByte((value shr 24) and 0xFF)
        }

        private fun java.io.DataOutputStream.writeShortLe(value: Int) {
            writeByte(value and 0xFF)
            writeByte((value shr 8) and 0xFF)
        }
    }
}
