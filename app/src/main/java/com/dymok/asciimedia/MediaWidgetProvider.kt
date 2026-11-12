package com.dymok.asciimedia

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

class MediaWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV = "com.dymok.asciimedia.ACTION_PREV"
        const val ACTION_PLAY_PAUSE = "com.dymok.asciimedia.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.dymok.asciimedia.ACTION_NEXT"
        const val ACTION_OPEN_APP = "com.dymok.asciimedia.ACTION_OPEN_APP"
        const val ACTION_OUTPUT = "com.dymok.asciimedia.ACTION_OUTPUT"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MediaWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids)
            }
        }

        fun updateWidgets(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val snapshot = MediaRepository.getCurrentSnapshot(context)
            val accentBright = context.getColor(android.R.color.system_accent1_100)
            val accentMuted = context.getColor(android.R.color.system_neutral2_400)
            val darkOnAccent = context.getColor(android.R.color.system_neutral1_900)

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_media)

                // Background style
                val bgRes = if (Prefs.isAmoledBg(context)) {
                    R.drawable.bg_widget_amoled
                } else {
                    R.drawable.bg_widget_glass
                }
                views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

                // Metadata text
                views.setTextViewText(R.id.tv_source_badge, snapshot.sourceLabel)
                views.setTextViewText(R.id.tv_output_badge, snapshot.outputLabel)
                views.setTextViewText(R.id.tv_title, snapshot.title)
                views.setTextViewText(R.id.tv_artist, snapshot.artist)

                // Album art vs placeholder icon
                if (snapshot.albumArt != null) {
                    views.setViewVisibility(R.id.iv_album_art, View.VISIBLE)
                    views.setViewVisibility(R.id.iv_placeholder_icon, View.GONE)
                    views.setImageViewBitmap(R.id.iv_album_art, snapshot.albumArt)
                } else {
                    views.setViewVisibility(R.id.iv_album_art, View.GONE)
                    views.setViewVisibility(R.id.iv_placeholder_icon, View.VISIBLE)
                    views.setInt(R.id.iv_placeholder_icon, "setColorFilter", accentBright)
                }

                // ASCII Progress bar with Material You colors
                val asciiBar = buildAsciiProgress(
                    positionMs = snapshot.positionMs,
                    durationMs = snapshot.durationMs,
                    activeColor = accentBright,
                    inactiveColor = accentMuted
                )
                views.setTextViewText(R.id.tv_ascii_progress, asciiBar)

                // Transport buttons & dynamic tinting
                views.setImageViewResource(
                    R.id.btn_play_pause,
                    if (snapshot.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                views.setInt(R.id.btn_play_pause, "setColorFilter", darkOnAccent)
                views.setInt(R.id.btn_prev, "setColorFilter", accentBright)
                views.setInt(R.id.btn_next, "setColorFilter", accentBright)
                views.setInt(R.id.btn_output, "setColorFilter", accentBright)

                // PendingIntents
                views.setOnClickPendingIntent(
                    R.id.btn_prev,
                    buildActionIntent(context, ACTION_PREV, 101)
                )
                views.setOnClickPendingIntent(
                    R.id.btn_play_pause,
                    buildActionIntent(context, ACTION_PLAY_PAUSE, 102)
                )
                views.setOnClickPendingIntent(
                    R.id.btn_next,
                    buildActionIntent(context, ACTION_NEXT, 103)
                )
                views.setOnClickPendingIntent(
                    R.id.btn_output,
                    buildActionIntent(context, ACTION_OUTPUT, 104)
                )
                views.setOnClickPendingIntent(
                    R.id.top_info_container,
                    buildActionIntent(context, ACTION_OPEN_APP, 105)
                )

                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun buildActionIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, MediaWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildAsciiProgress(
            positionMs: Long,
            durationMs: Long,
            activeColor: Int,
            inactiveColor: Int
        ): CharSequence {
            val totalSlots = 16
            val ratio = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val filledSlots = (ratio * totalSlots).toInt().coerceIn(0, totalSlots)

            val sb = StringBuilder("[")
            for (i in 0 until totalSlots) {
                when {
                    i < filledSlots -> sb.append('━')
                    i == filledSlots && durationMs > 0L -> sb.append('●')
                    else -> sb.append('─')
                }
            }
            sb.append("] ")
            sb.append(formatTime(positionMs))
            if (durationMs > 0L) {
                sb.append(" / ").append(formatTime(durationMs))
            }

            val fullText = sb.toString()
            val spannable = SpannableString(fullText)
            val activeEnd = (1 + filledSlots + if (durationMs > 0L && filledSlots < totalSlots) 1 else 0)
                .coerceAtMost(fullText.length)

            spannable.setSpan(
                ForegroundColorSpan(activeColor),
                0,
                activeEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (activeEnd < fullText.length) {
                spannable.setSpan(
                    ForegroundColorSpan(inactiveColor),
                    activeEnd,
                    fullText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return spannable
        }

        private fun formatTime(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return String.format(Locale.US, "%02d:%02d", min, sec)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_PREV -> {
                MediaRepository.getPrimaryController(context)?.transportControls?.skipToPrevious()
                requestUpdate(context)
            }
            ACTION_PLAY_PAUSE -> {
                val controller = MediaRepository.getPrimaryController(context)
                if (controller != null) {
                    val isPlaying = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                    if (isPlaying) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                } else {
                    openActiveOrLastPlayer(context)
                }
                requestUpdate(context)
            }
            ACTION_NEXT -> {
                MediaRepository.getPrimaryController(context)?.transportControls?.skipToNext()
                requestUpdate(context)
            }
            ACTION_OPEN_APP -> {
                openActiveOrLastPlayer(context)
            }
            ACTION_OUTPUT -> {
                openAudioOutputSwitcher(context)
            }
        }
    }

    private fun openActiveOrLastPlayer(context: Context) {
        if (!MediaRepository.isNotificationAccessGranted(context)) {
            val setupIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(setupIntent)
            return
        }

        val controller = MediaRepository.getPrimaryController(context)
        try {
            val sessionPending = controller?.sessionActivity
            if (sessionPending != null) {
                sessionPending.send()
                return
            }
        } catch (_: Exception) {
        }

        val targetPkg = controller?.packageName ?: Prefs.getLastPackage(context)
        if (!targetPkg.isNullOrBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        }

        val fallback = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallback)
        } catch (_: Exception) {
        }
    }

    private fun openAudioOutputSwitcher(context: Context) {
        // Try Android system media output dialog first, then volume panel, then Bluetooth settings
        val intents = listOf(
            Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").apply {
                setPackage("com.android.systemui")
                putExtra("package_name", MediaRepository.getPrimaryController(context)?.packageName ?: context.packageName)
            },
            Intent(Settings.Panel.ACTION_VOLUME),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )

        for (candidate in intents) {
            try {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(candidate)
                return
            } catch (_: Exception) {
            }
        }

        // Last resort: trigger system volume UI
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {
        }
    }
}
