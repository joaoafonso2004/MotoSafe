package com.example.motosafe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.load
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    // UI Elements
    private lateinit var playerView: PlayerView
    private lateinit var normalLayout: LinearLayout
    private lateinit var emergencyLayout: RelativeLayout
    private lateinit var tvSensorStatus: TextView
    private lateinit var tvMusicTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvPlayState: TextView
    private lateinit var btnCancelAlert: Button
    private lateinit var btnCall112: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnShuffle: TextView
    private lateinit var btnRepeat: TextView
    private lateinit var btnPlayPause: TextView
    private lateinit var btnPrevious: TextView
    private lateinit var btnNext: TextView
    private lateinit var imgAlbumArt: ImageView
    private lateinit var imgBackgroundBlur: ImageView

    // ExoPlayer
    private var player: ExoPlayer? = null

    // Sensores
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null

    // Handlers
    private val updateSeekBarHandler = Handler(Looper.getMainLooper())
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var pendingGestureRunnable: Runnable? = null

    // Playlist state
    private var currentPlaylistIndex = 0
    private var currentTrackIndex = 0

    // Controlo de Gestos
    private var gestureCount = 0
    private var lastGestureTime = 0L
    private val gestureTimeout = 500L
    private var gestureStartTime = 0L
    private var isNear = false

    // Impact detection
    private var freeFallStartTime = 0L
    private var isInFreeFall = false
    private var lastAcceleration = 9.8f

    // Emergência
    private var alarmPlayer: MediaPlayer? = null
    private var isEmergencyMode = false

    // Player modes
    private var isShuffleEnabled = false
    private var isRepeatEnabled = false

    private var lightSensor: Sensor? = null

    private var lastLightValue = 0f

    private val LIGHT_THRESHOLD = 15f // Sensibilidade: quanto maior, mais sombra é precisa

    private val selectTrackLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val playlistIndex = data?.getIntExtra("PLAYLIST_INDEX", 0) ?: 0
                val trackIndex = data?.getIntExtra("TRACK_INDEX", 0) ?: 0

                loadTrack(playlistIndex, trackIndex)

                btnPlayPause.isEnabled = true
                btnNext.isEnabled = true
                btnPrevious.isEnabled = true
                btnShuffle.isEnabled = true
                btnRepeat.isEnabled = true
            }
        }

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                makeEmergencyCall()
            } else {
                Toast.makeText(this, "Permissão de chamada negada", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar UI
        playerView = findViewById(R.id.playerView)
        normalLayout = findViewById(R.id.normalLayout)
        emergencyLayout = findViewById(R.id.emergencyLayout)
        tvSensorStatus = findViewById(R.id.tvSensorStatus)
        tvMusicTitle = findViewById(R.id.tvMusicTitle)
        tvArtist = findViewById(R.id.tvArtist)
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        imgBackgroundBlur = findViewById(R.id.imgBackgroundBlur)
        tvPlayState = findViewById(R.id.tvPlayState)
        btnCancelAlert = findViewById(R.id.btnCancelAlert)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)

        btnPlayPause.isEnabled = false
        btnNext.isEnabled = false
        btnPrevious.isEnabled = false
        btnShuffle.isEnabled = false
        btnRepeat.isEnabled = false

        setupPlayerControls()

        // Sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        updateSensorAvailabilityUI()

        // Player
        initializePlayer()

        // Botão 112
        createCall112Button()

        btnCancelAlert.setOnClickListener {
            cancelEmergency()
        }

        // Modo demo
        tvMusicTitle.setOnLongClickListener {
            if (!isEmergencyMode) {
                triggerEmergency()
                Toast.makeText(this, "🎬 Modo Demo: Emergência ativada", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // Abrir playlists
        findViewById<Button>(R.id.btnOpenPlaylist).setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            selectTrackLauncher.launch(intent)
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayState()
                if (isPlaying) {
                    startSeekBarUpdate()
                } else {
                    stopSeekBarUpdate()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onTrackEnded()
                }
                updatePlayState()
            }
        })
    }

    private fun updateSensorAvailabilityUI() {
        when {
            proximitySensor == null && accelerometer == null -> {
                setSensorStatus("⚠ Sensores indisponíveis", 0xFFFF6600.toInt())
            }
            proximitySensor == null -> {
                setSensorStatus("⚠ Sem sensor de proximidade", 0xFFFFAA00.toInt())
            }
            accelerometer == null -> {
                setSensorStatus("⚠ Sem acelerómetro", 0xFFFFAA00.toInt())
            }
            else -> {
                setSensorStatus("ACTIVE", 0xFF00FF88.toInt())
            }
        }
    }

    private fun setSensorStatus(text: String, color: Int) {
        runOnUiThread {
            tvSensorStatus.text = text
            tvSensorStatus.setTextColor(color)
        }
    }

    private fun createCall112Button() {
        btnCall112 = Button(this).apply {
            text = "📞 LIGAR PARA 112"
            textSize = 18f
            setPadding(20, 60, 20, 60)
            setBackgroundColor(0xFFFF6600.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(40, 0, 40, 40)
            }
            setOnClickListener {
                showCall112Confirmation()
            }
        }

        emergencyLayout.addView(btnCall112)
    }

    private fun getCurrentPlaylistOrNull(): Playlist? {
        return MusicLibrary.playlists.getOrNull(currentPlaylistIndex)
    }

    private fun getCurrentTrackOrNull(): Track? {
        val playlist = getCurrentPlaylistOrNull() ?: return null
        return playlist.tracks.getOrNull(currentTrackIndex)
    }

    private fun loadTrack(playlistIndex: Int, trackIndex: Int) {
        val playlist = MusicLibrary.playlists.getOrNull(playlistIndex)

        if (playlist == null) {
            Toast.makeText(this, "Playlist inválida", Toast.LENGTH_SHORT).show()
            return
        }

        if (playlist.tracks.isEmpty()) {
            Toast.makeText(this, "A playlist não tem músicas", Toast.LENGTH_SHORT).show()
            return
        }

        val track = playlist.tracks.getOrNull(trackIndex)

        if (track == null) {
            Toast.makeText(this, "Música inválida", Toast.LENGTH_SHORT).show()
            return
        }

        currentPlaylistIndex = playlistIndex
        currentTrackIndex = trackIndex

        val mediaItem = MediaItem.fromUri(track.url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        tvMusicTitle.text = track.title
        tvArtist.text = track.artist

        if (track.imageUrl.isNotEmpty()) {
            imgAlbumArt.load(track.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_background)
            }
            imgBackgroundBlur.load(track.imageUrl) {
                crossfade(true)
            }
        } else {
            imgAlbumArt.setImageResource(R.drawable.ic_launcher_background)
            imgBackgroundBlur.setImageResource(R.drawable.ic_launcher_background)
        }

        updatePlayState()
        startSeekBarUpdate()
        btnPlayPause.text = "⏸"
    }

    private fun togglePlayPause() {
        if (player == null) return

        if (player?.isPlaying == true) {
            player?.pause()
        } else {
            player?.play()
        }

        updatePlayState()
    }

    private fun nextTrack() {
        val playlist = getCurrentPlaylistOrNull() ?: return
        if (playlist.tracks.isEmpty()) return

        currentTrackIndex = if (currentTrackIndex < playlist.tracks.size - 1) {
            currentTrackIndex + 1
        } else {
            0
        }

        loadTrack(currentPlaylistIndex, currentTrackIndex)
    }

    private fun previousTrack() {
        val playlist = getCurrentPlaylistOrNull() ?: return
        if (playlist.tracks.isEmpty()) return

        currentTrackIndex = if (currentTrackIndex > 0) {
            currentTrackIndex - 1
        } else {
            playlist.tracks.size - 1
        }

        loadTrack(currentPlaylistIndex, currentTrackIndex)
    }

    private fun previousTrackSmart() {
        val currentPosition = player?.currentPosition ?: 0

        if (currentPosition < 5000) {
            previousTrack()
        } else {
            player?.seekTo(0)
            Toast.makeText(this, "🔄 Restart", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayState() {
        player?.let {
            val state = when {
                it.isPlaying -> "▶ A TOCAR"
                it.playbackState == Player.STATE_BUFFERING -> "⏳ A CARREGAR..."
                it.playbackState == Player.STATE_READY -> "⏸ PAUSADO"
                else -> "⏹ PARADO"
            }

            tvPlayState.text = state
            btnPlayPause.text = if (it.isPlaying) "⏸" else "▶"
        }
    }

    private fun clearPendingGestureCallback() {
        pendingGestureRunnable?.let { gestureHandler.removeCallbacks(it) }
        pendingGestureRunnable = null
    }

    private fun resetGestureState() {
        clearPendingGestureCallback()
        gestureCount = 0
        isNear = false
        lastGestureTime = 0L
        gestureStartTime = 0L
        isInFreeFall = false
    }

    private fun triggerEmergency() {
        if (isEmergencyMode) return

        isEmergencyMode = true
        resetGestureState()

        runOnUiThread {
            player?.pause()
            stopSeekBarUpdate()

            normalLayout.visibility = View.GONE
            emergencyLayout.visibility = View.VISIBLE

            Toast.makeText(this, "🚨 ALERTA DE EMERGÊNCIA ATIVADO!", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelEmergency() {
        isEmergencyMode = false
        resetGestureState()

        emergencyLayout.visibility = View.GONE
        normalLayout.visibility = View.VISIBLE

        player?.play()
        updatePlayState()
        startSeekBarUpdate()

        setSensorStatus("ACTIVE", 0xFF00FF88.toInt())

        Toast.makeText(this, "✓ Alerta cancelado", Toast.LENGTH_SHORT).show()
    }

    private fun showCall112Confirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirmação de Chamada")
            .setMessage("Tem a certeza que deseja ligar para o 112 (Emergências)?")
            .setPositiveButton("SIM, LIGAR") { _, _ ->
                requestCallPermissionAndCall()
            }
            .setNegativeButton("CANCELAR", null)
            .setCancelable(true)
            .show()
    }

    private fun requestCallPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            makeEmergencyCall()
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun makeEmergencyCall() {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:112")
        }
        startActivity(callIntent)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (isEmergencyMode) return


        when (event?.sensor?.type) {
            Sensor.TYPE_PROXIMITY -> handleProximity(event)
            Sensor.TYPE_LIGHT -> handleLightSensor(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    private fun handleLightSensor(event: SensorEvent) {
        if (isEmergencyMode) return
        if (proximitySensor != null && isNear) return
        val currentLight = event.values[0]

        // Queda súbita de luz = sombra da mão a aproximar-se
        if (lastLightValue > 20 && lastLightValue - currentLight > LIGHT_THRESHOLD) {
            triggerGestureStart()
        }

        // Luz voltou ao normal = mão afastou-se
        if (currentLight - lastLightValue > LIGHT_THRESHOLD && isNear) {
            triggerGestureEnd()
        }

        lastLightValue = currentLight
    }

    // Criamos esta função para ser usada tanto pelo Proximity como pela Luz
    private fun triggerGestureStart() {
        val currentTime = System.currentTimeMillis()
        isNear = true
        gestureStartTime = currentTime

        gestureCount = if (currentTime - lastGestureTime < gestureTimeout) {
            gestureCount + 1
        } else {
            1
        }

        setSensorStatus("● Gesto $gestureCount...", 0xFFFFAA00.toInt())
    }

    private fun handleProximity(event: SensorEvent) {
        if (isEmergencyMode) return
        if (proximitySensor == null) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5f
        val isObjectNear = distance < maxRange

        if (isObjectNear) {
            if (!isNear) {
                triggerGestureStart()
            }
        } else {
            if (isNear) {
                triggerGestureEnd()
            }
        }
    }

    private fun triggerGestureEnd() {
        val currentTime = System.currentTimeMillis()
        isNear = false
        lastGestureTime = currentTime

        clearPendingGestureCallback()

        val capturedGestureTime = currentTime
        pendingGestureRunnable = Runnable {
            if (capturedGestureTime == lastGestureTime && !isEmergencyMode) {
                when (gestureCount) {
                    1 -> {
                        togglePlayPause()
                        Toast.makeText(this, "⏯️ Play/Pause", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        nextTrack()
                        Toast.makeText(this, "⏭️ Próxima", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        previousTrackSmart()
                        Toast.makeText(this, "⏮️ Anterior/Restart", Toast.LENGTH_SHORT).show()
                    }
                }

                gestureCount = 0
                setSensorStatus("ACTIVE", 0xFF00FF88.toInt())
            }
        }

        gestureHandler.postDelayed(pendingGestureRunnable!!, gestureTimeout)
    }

    private fun handleAccelerometer(event: SensorEvent) {
        if (isEmergencyMode) return
        if (accelerometer == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration = sqrt(x * x + y * y + z * z)

        if (acceleration < 2.0f) {
            if (!isInFreeFall) {
                isInFreeFall = true
                freeFallStartTime = System.currentTimeMillis()
            } else {
                if (System.currentTimeMillis() - freeFallStartTime > 300) {
                    triggerEmergency()
                    runOnUiThread {
                        Toast.makeText(this, "🚨 Queda detetada!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            isInFreeFall = false
        }

        if (acceleration > 20.0f && !isEmergencyMode) {
            triggerEmergency()
            runOnUiThread {
                Toast.makeText(this, "🚨 Impacto detetado!", Toast.LENGTH_LONG).show()
            }
        }

        lastAcceleration = acceleration
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // sem ação
    }

    override fun onResume() {
        super.onResume()

        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }

        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (player?.isPlaying == true) {
            startSeekBarUpdate()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        clearPendingGestureCallback()
        stopSeekBarUpdate()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPendingGestureCallback()
        stopSeekBarUpdate()
        sensorManager.unregisterListener(this)
        playerView.player = null
        player?.release()
        player = null
        alarmPlayer?.release()
        alarmPlayer = null
    }

    private fun setupPlayerControls() {
        btnPlayPause.setOnClickListener {
            player?.let {
                if (it.isPlaying) {
                    it.pause()
                    stopSeekBarUpdate()
                } else {
                    it.play()
                    startSeekBarUpdate()
                }
                updatePlayState()
            }
        }

        btnPrevious.setOnClickListener {
            previousTrack()
        }

        btnNext.setOnClickListener {
            nextTrack()
        }

        btnShuffle.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            updateShuffleButton()
            Toast.makeText(
                this,
                if (isShuffleEnabled) "🔀 Shuffle ON" else "🔁 Shuffle OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnRepeat.setOnClickListener {
            isRepeatEnabled = !isRepeatEnabled
            updateRepeatButton()
            Toast.makeText(
                this,
                if (isRepeatEnabled) "🔁 Repeat ON" else "➡️ Repeat OFF",
                Toast.LENGTH_SHORT
            ).show()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        startSeekBarUpdate()
    }

    private val seekBarUpdateRunnable = object : Runnable {
        override fun run() {
            player?.let {
                val currentPosition = it.currentPosition.toInt()
                val duration = it.duration.toInt()

                if (duration > 0) {
                    seekBar.max = duration
                    seekBar.progress = currentPosition
                    tvCurrentTime.text = formatTime(currentPosition)
                    tvTotalTime.text = formatTime(duration)
                }
            }

            if (player?.isPlaying == true) {
                updateSeekBarHandler.postDelayed(this, 500)
            }
        }
    }

    private fun startSeekBarUpdate() {
        updateSeekBarHandler.removeCallbacks(seekBarUpdateRunnable)
        updateSeekBarHandler.post(seekBarUpdateRunnable)
    }

    private fun stopSeekBarUpdate() {
        updateSeekBarHandler.removeCallbacks(seekBarUpdateRunnable)
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateShuffleButton() {
        if (isShuffleEnabled) {
            btnShuffle.setBackgroundResource(R.drawable.button_shuffle_on)
            btnShuffle.setTextColor(0xFF000000.toInt())
        } else {
            btnShuffle.setBackgroundResource(R.drawable.button_shuffle_off)
            btnShuffle.setTextColor(0xFF666666.toInt())
        }
    }

    private fun updateRepeatButton() {
        if (isRepeatEnabled) {
            btnRepeat.setBackgroundResource(R.drawable.button_shuffle_on)
            btnRepeat.setTextColor(0xFF000000.toInt())
        } else {
            btnRepeat.setBackgroundResource(R.drawable.button_shuffle_off)
            btnRepeat.setTextColor(0xFF666666.toInt())
        }
    }

    private fun onTrackEnded() {
        if (isShuffleEnabled) {
            val nonEmptyPlaylists = MusicLibrary.playlists.withIndex()
                .filter { it.value.tracks.isNotEmpty() }

            if (nonEmptyPlaylists.isEmpty()) {
                Toast.makeText(this, "Não há músicas disponíveis", Toast.LENGTH_SHORT).show()
                return
            }

            val randomPlaylistEntry = nonEmptyPlaylists.random()
            val randomPlaylistIndex = randomPlaylistEntry.index
            val randomPlaylist = randomPlaylistEntry.value
            val randomTrackIndex = (0 until randomPlaylist.tracks.size).random()

            loadTrack(randomPlaylistIndex, randomTrackIndex)
            Toast.makeText(
                this,
                "🔀 Random: ${randomPlaylist.tracks[randomTrackIndex].title}",
                Toast.LENGTH_SHORT
            ).show()

        } else if (isRepeatEnabled) {
            player?.seekTo(0)
            player?.play()

        } else {
            val playlist = getCurrentPlaylistOrNull() ?: return

            if (playlist.tracks.isEmpty()) {
                Toast.makeText(this, "Playlist vazia", Toast.LENGTH_SHORT).show()
                return
            }

            if (currentTrackIndex < playlist.tracks.size - 1) {
                currentTrackIndex++
                loadTrack(currentPlaylistIndex, currentTrackIndex)
            } else {
                Toast.makeText(this, "✅ Playlist terminada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}