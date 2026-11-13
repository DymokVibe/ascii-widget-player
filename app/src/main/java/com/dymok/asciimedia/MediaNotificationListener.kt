package com.dymok.asciimedia

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListener : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val progressTicker = object : Runnable {
        override fun run() {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying && powerManager.isInteractive) {
                MediaWidgetProvider.requestUpdate(applicationContext)
                mainHandler.postDelayed(this, 2500L)
            }
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            MediaWidgetProvider.requestUpdate(applicationContext)
            syncTicker()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            MediaWidgetProvider.requestUpdate(applicationContext)
        }

        override fun onSessionDestroyed() {
            rebindActiveSession()
            MediaWidgetProvider.requestUpdate(applicationContext)
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachToBestController(controllers)
            MediaWidgetProvider.requestUpdate(applicationContext)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MediaNotificationListener::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            rebindActiveSession()
            MediaWidgetProvider.requestUpdate(applicationContext)
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanup()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (activeController == null) {
            rebindActiveSession()
        }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun rebindActiveSession() {
        try {
            val component = ComponentName(this, MediaNotificationListener::class.java)
            val controllers = sessionManager?.getActiveSessions(component)
            attachToBestController(controllers)
        } catch (_: Exception) {
        }
    }

    private fun attachToBestController(controllers: List<MediaController>?) {
        val chosen = controllers?.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers?.firstOrNull()

        if (chosen?.sessionToken == activeController?.sessionToken) {
            syncTicker()
            return
        }

        activeController?.unregisterCallback(controllerCallback)
        activeController = chosen
        activeController?.registerCallback(controllerCallback, mainHandler)
        syncTicker()
    }

    private fun syncTicker() {
        mainHandler.removeCallbacks(progressTicker)
        val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (isPlaying) {
            mainHandler.postDelayed(progressTicker, 2500L)
        }
    }

    private fun cleanup() {
        mainHandler.removeCallbacks(progressTicker)
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (_: Exception) {
        }
        activeController = null
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {
        }
    }
}
