package com.example.motosafe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MusicService : Service() {

    private var player: ExoPlayer? = null
    private val binder = MusicBinder()

    private var currentPlaylistIndex = 0
    private var currentTrackIndex = 0

    companion object {
        const val CHANNEL_ID = "MusicServiceChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_STOP = "ACTION_STOP"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player?.play()
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT -> nextTrack()
            ACTION_PREVIOUS -> previousTrack()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    nextTrack()
                }
            }
        })
    }

    fun loadTrack(playlistIndex: Int, trackIndex: Int) {
        currentPlaylistIndex = playlistIndex
        currentTrackIndex = trackIndex

        val track = MusicLibrary.playlists[playlistIndex].tracks[trackIndex]
        val mediaItem = MediaItem.fromUri(track.url)

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun getPlayer(): ExoPlayer? = player

    fun getCurrentTrack(): Track {
        return MusicLibrary.playlists[currentPlaylistIndex].tracks[currentTrackIndex]
    }

    fun getCurrentPlaylistIndex() = currentPlaylistIndex
    fun getCurrentTrackIndex() = currentTrackIndex

    fun nextTrack() {
        val playlist = MusicLibrary.playlists[currentPlaylistIndex]
        if (currentTrackIndex < playlist.tracks.size - 1) {
            currentTrackIndex++
        } else {
            currentTrackIndex = 0
        }
        loadTrack(currentPlaylistIndex, currentTrackIndex)
    }

    fun previousTrack() {
        if (currentTrackIndex > 0) {
            currentTrackIndex--
        } else {
            val playlist = MusicLibrary.playlists[currentPlaylistIndex]
            currentTrackIndex = playlist.tracks.size - 1
        }
        loadTrack(currentPlaylistIndex, currentTrackIndex)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val track = getCurrentTrack()

        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).apply {
                action = if (player?.isPlaying == true) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Previous", previousIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                if (player?.isPlaying == true) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(R.drawable.ic_launcher_foreground, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}