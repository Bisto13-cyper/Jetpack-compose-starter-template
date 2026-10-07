package com.superapp.app.features.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Everything the ring service / activity needs to know about one ringing alarm or timer. */
data class RingRequest(
    val kind: String,
    val title: String,
    val message: String,
    val soundUri: String?,
    val volumePercent: Int,
    val vibrate: Boolean,
    val snoozeMinutes: Int
) {
    fun putInto(intent: Intent): Intent = intent
        .putExtra(X_KIND, kind).putExtra(X_TITLE, title).putExtra(X_MESSAGE, message)
        .putExtra(X_SOUND, soundUri).putExtra(X_VOLUME, volumePercent)
        .putExtra(X_VIBRATE, vibrate).putExtra(X_SNOOZE, snoozeMinutes)

    companion object {
        const val KIND_ALARM = "alarm"
        const val KIND_TIMER = "timer"
        private const val X_KIND = "ring_kind"
        private const val X_TITLE = "ring_title"
        private const val X_MESSAGE = "ring_message"
        private const val X_SOUND = "ring_sound"
        private const val X_VOLUME = "ring_volume"
        private const val X_VIBRATE = "ring_vibrate"
        private const val X_SNOOZE = "ring_snooze"

        fun fromIntent(intent: Intent?): RingRequest? {
            if (intent == null || !intent.hasExtra(X_TITLE)) return null
            return RingRequest(
                kind = intent.getStringExtra(X_KIND) ?: KIND_ALARM,
                title = intent.getStringExtra(X_TITLE) ?: "",
                message = intent.getStringExtra(X_MESSAGE) ?: "",
                soundUri = intent.getStringExtra(X_SOUND),
                volumePercent = intent.getIntExtra(X_VOLUME, 100),
                vibrate = intent.getBooleanExtra(X_VIBRATE, true),
                snoozeMinutes = intent.getIntExtra(X_SNOOZE, 0)
            )
        }
    }
}

/**
 * Foreground service that rings an alarm/timer: loops the chosen sound on the ALARM audio stream,
 * vibrates, shows a full-screen notification with Stop / Snooze, and stops itself after 10 minutes.
 */
class AlarmRingService : Service() {

    private var player: MediaPlayer? = null
    private var current: RingRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_SNOOZE -> {
                current?.let { AlarmScheduler.scheduleSnooze(this, it) }
                stopRinging()
            }
            else -> {
                val request = RingRequest.fromIntent(intent)
                if (request == null) {
                    stopSelf()
                } else {
                    startRinging(request)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(request: RingRequest) {
        current = request
        ringing = true
        ensureChannel(this)
        val notification = buildNotification(this, request)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        releasePlayer()
        startPlayer(request)
        if (request.vibrate) startVibration()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun startPlayer(request: RingRequest) {
        if (request.soundUri == ClockSounds.SILENT) return
        val candidates = buildList {
            request.soundUri?.let { add(Uri.parse(it)) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
        }
        val volume = (request.volumePercent.coerceIn(0, 100)) / 100f
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.setVolume(volume, volume)
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                mp.release()
            }
        }
    }

    private fun releasePlayer() {
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        player = null
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 600, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            v.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(autoStop)
        releasePlayer()
        vibrator()?.cancel()
        ringing = false
        current = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        releasePlayer()
        vibrator()?.cancel()
        ringing = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.superapp.app.clock.ACTION_RING_STOP"
        const val ACTION_SNOOZE = "com.superapp.app.clock.ACTION_RING_SNOOZE"
        private const val CHANNEL_ID = "clock_alarm_ring"
        private const val FALLBACK_CHANNEL_ID = "clock_alarm_fallback"
        private const val NOTIFICATION_ID = 4101
        private const val AUTO_STOP_MS = 10 * 60 * 1000L

        /** True while an alarm/timer is ringing; read by [AlarmRingActivity]. */
        @Volatile var ringing: Boolean = false
            private set

        fun start(context: Context, request: RingRequest) {
            val intent = request.putInto(Intent(context, AlarmRingService::class.java))
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Foreground-service start was refused by the OS: still alert the user.
                postFallbackNotification(context, request)
            }
        }

        fun stop(context: Context) {
            if (!ringing) return
            context.startService(Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Shown while an alarm or timer is ringing"
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }

        private fun ringIntent(context: Context, request: RingRequest): PendingIntent {
            val intent = request.putInto(Intent(context, AlarmRingActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun serviceAction(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getService(
                context, code, Intent(context, AlarmRingService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun buildNotification(context: Context, request: RingRequest): Notification {
            val b = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(request.title)
                .setContentText(request.message)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(ringIntent(context, request))
                .setFullScreenIntent(ringIntent(context, request), true)
                .addAction(0, "Stop", serviceAction(context, ACTION_STOP, 2))
            if (request.snoozeMinutes > 0) {
                b.addAction(0, "Snooze ${request.snoozeMinutes} min", serviceAction(context, ACTION_SNOOZE, 3))
            }
            return b.build()
        }

        private fun postFallbackNotification(context: Context, request: RingRequest) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(FALLBACK_CHANNEL_ID, "Alarms (fallback)", NotificationManager.IMPORTANCE_HIGH)
                ch.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
                nm.createNotificationChannel(ch)
            }
            val n = NotificationCompat.Builder(context, FALLBACK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(request.title)
                .setContentText(request.message)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(ringIntent(context, request))
                .setFullScreenIntent(ringIntent(context, request), true)
                .build()
            try {
                nm.notify(NOTIFICATION_ID + 1, n)
            } catch (_: SecurityException) {
                // Notification permission denied: nothing more the app may do.
            }
        }
    }
}
