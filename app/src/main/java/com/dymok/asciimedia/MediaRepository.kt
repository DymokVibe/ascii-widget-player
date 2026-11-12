package com.dymok.asciimedia

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings

data class MediaSnapshot(
    val isActive: Boolean,
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val sourceLabel: String,
    val outputLabel: String,
    val positionMs: Long,
    val durationMs: Long,
    val albumArt: Bitmap?,
    val packageName: String?
)

object MediaRepository {

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val component = ComponentName(context, MediaNotificationListener::class.java)
        return enabledListeners.contains(component.flattenToString()) ||
            enabledListeners.contains(component.flattenToShortString())
    }

    fun getPrimaryController(context: Context): MediaController? {
        if (!isNotificationAccessGranted(context)) return null
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, MediaNotificationListener::class.java)
            val controllers = manager.getActiveSessions(component)
            if (controllers.isEmpty()) return null

            // Prefer actively playing session, otherwise most recent active session
            controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun getCurrentSnapshot(context: Context): MediaSnapshot {
        val outputLabel = detectAudioOutput(context)
        val controller = getPrimaryController(context)
            ?: return MediaSnapshot(
                isActive = false,
                isPlaying = false,
                title = if (isNotificationAccessGranted(context)) "No active stream" else "Permission needed",
                artist = if (isNotificationAccessGranted(context)) "Tap to launch player" else "Tap to grant Notification Access",
                sourceLabel = "[ IDLE // STANDBY ]",
                outputLabel = outputLabel,
                positionMs = 0L,
                durationMs = 0L,
                albumArt = null,
                packageName = Prefs.getLastPackage(context)
            )

        val pkg = controller.packageName
        Prefs.setLastPackage(context, pkg)

        val stateObj = controller.playbackState
        val state = stateObj?.state ?: PlaybackState.STATE_NONE
        val isPlaying = state == PlaybackState.STATE_PLAYING

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() } ?: "Unknown Track"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: resolveAppName(context, pkg)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val rawPos = stateObj?.position?.coerceAtLeast(0L) ?: 0L
        val position = if (isPlaying && stateObj != null) {
            val elapsed = SystemClock.elapsedRealtime() - stateObj.lastPositionUpdateTime
            val speed = stateObj.playbackSpeed.takeIf { it > 0f } ?: 1f
            val estimated = rawPos + (elapsed * speed).toLong()
            if (duration > 0L) estimated.coerceAtMost(duration) else estimated
        } else {
            rawPos
        }

        val rawArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val processedArt = rawArt?.let { processCoverArt(context, it) }

        val statusPrefix = if (isPlaying) "PLAY" else "PAUSED"
        val appTag = resolveAppName(context, pkg).uppercase()

        return MediaSnapshot(
            isActive = true,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            sourceLabel = "[ $statusPrefix // $appTag ]",
            outputLabel = outputLabel,
            positionMs = position,
            durationMs = duration,
            albumArt = processedArt,
            packageName = pkg
        )
    }

    private fun resolveAppName(context: Context, packageName: String?): String {
        if (packageName.isNullOrBlank()) return "STREAM"
        return when {
            packageName.contains("spotify") -> "SPOTIFY"
            packageName.contains("youtube.music") -> "YT MUSIC"
            packageName.contains("youtube") -> "YOUTUBE"
            packageName.contains("telegram") -> "TELEGRAM"
            packageName.contains("poweramp") -> "POWERAMP"
            packageName.contains("vlc") -> "VLC"
            packageName.contains("firefox") -> "FIREFOX"
            packageName.contains("chrome") -> "CHROME"
            packageName.contains("soundcloud") -> "SOUNDCLOUD"
            else -> try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString().take(14)
            } catch (_: Exception) {
                "MEDIA"
            }
        }
    }

    private fun detectAudioOutput(context: Context): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasBt = devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
            val hasWired = devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
            when {
                hasBt -> "[ BT ]"
                hasWired -> "[ DAC ]"
                else -> "[ SPK ]"
            }
        } catch (_: Exception) {
            "[ OUT ]"
        }
    }

    private fun processCoverArt(context: Context, source: Bitmap): Bitmap {
        val targetSize = 160
        val cornerRadius = 28f

        // Center-crop square
        val dim = minOf(source.width, source.height).coerceAtLeast(1)
        val xOffset = (source.width - dim) / 2
        val yOffset = (source.height - dim) / 2
        val cropped = Bitmap.createBitmap(source, xOffset, yOffset, dim, dim)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped != source && !cropped.isRecycled) {
            cropped.recycle()
        }

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, targetSize, targetSize)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        if (Prefs.isMonetCover(context)) {
            // Subtle desaturation + Material You accent tint so cover blends with home screen
            val cm = ColorMatrix().apply { setSaturation(0.25f) }
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(scaled, rect, rect, paint)

            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val accent = context.getColor(android.R.color.system_accent1_200)
                colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.MULTIPLY)
                alpha = 95
            }
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, tintPaint)
        } else {
            canvas.drawBitmap(scaled, rect, rect, paint)
        }

        if (scaled != source && !scaled.isRecycled) {
            scaled.recycle()
        }
        return output
    }
}
