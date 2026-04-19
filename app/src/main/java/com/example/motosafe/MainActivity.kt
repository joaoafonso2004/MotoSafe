package com.example.motosafe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.sqrt
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.media3.common.Player
import android.os.Handler
import android.os.Looper
import coil.load

class MainActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private const val REQUEST_SELECT_TRACK = 1001
    }

    // UI Elements
    private lateinit var playerView: PlayerView
    private lateinit var normalLayout: LinearLayout
    private lateinit var emergencyLayout: RelativeLayout
    private lateinit var tvSensorStatus: TextView
    private lateinit var tvMusicTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvPlayState: TextView
    private lateinit var btnCancelAlert: Button

    private lateinit var seekBar: SeekBar

    private lateinit var tvCurrentTime: TextView

    private lateinit var tvTotalTime: TextView

    private lateinit var btnShuffle: TextView

    private lateinit var btnRepeat: TextView

    private lateinit var btnPlayPause: TextView

    private lateinit var btnPrevious: TextView

    private lateinit var btnNext: TextView
    private lateinit var btnCall112: Button

    private lateinit var imgAlbumArt: ImageView

    private lateinit var imgBackgroundBlur: ImageView

    // ExoPlayer (para música)
    private var player: ExoPlayer? = null

    // Sensores
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null

    private var updateSeekBarHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentPlaylistIndex = 0
    private var currentTrackIndex = 0

    private fun getCurrentPlaylist(): Playlist {
        return MusicLibrary.playlists[currentPlaylistIndex]
    }

    private fun getCurrentTrack(): Track {
        return getCurrentPlaylist().tracks[currentTrackIndex]
    }

    // Controlo de Gestos
    private var gestureStartTime = 0L
    private var isNear = false

    // Alarme de Emergência
    private var alarmPlayer: MediaPlayer? = null
    private var isEmergencyMode = false

    private var isShuffleEnabled = false

    private var isRepeatEnabled = false

    // Permissão de chamada
    private val CALL_PERMISSION_CODE = 100

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

        // Criar botão de chamada 112 programaticamente
        createCall112Button()

        // Inicializar Sensores
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Inicializar Player
        initializePlayer()

        // Botão Cancelar Alerta
        btnCancelAlert.setOnClickListener {
            cancelEmergency()
        }
        // MODO DEMO: Clique longo no título ativa emergência
        findViewById<TextView>(R.id.tvMusicTitle).setOnLongClickListener {
            if (!isEmergencyMode) {
                triggerEmergency()
                Toast.makeText(this, "🎬 Modo Demo: Emergência ativada", Toast.LENGTH_SHORT).show()
            }
            true
        }
        // Botão abrir playlists
        findViewById<Button>(R.id.btnOpenPlaylist).setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            startActivityForResult(intent, REQUEST_SELECT_TRACK)
        }
    }

    private fun createCall112Button() {
        // Criar botão programaticamente
        btnCall112 = Button(this).apply {
            text = "📞 LIGAR PARA 112"
            textSize = 18f
            setPadding(20, 60, 20, 60)
            setBackgroundColor(0xFFFF6600.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
            setOnClickListener {
                showCall112Confirmation()
            }
        }

        // Adicionar ao layout de emergência
        emergencyLayout.addView(btnCall112)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()

        // ADICIONA ESTE LISTENER ⬇️
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

    private fun loadTrack(playlistIndex: Int, trackIndex: Int) {
        currentPlaylistIndex = playlistIndex
        currentTrackIndex = trackIndex

        val track = getCurrentTrack()

        // Carregar música
        val mediaItem = MediaItem.fromUri(track.url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

        // Atualizar textos
        tvMusicTitle.text = track.title
        tvArtist.text = track.artist

        // Carregar imagens
        if (track.imageUrl.isNotEmpty()) {
            imgAlbumArt.load(track.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_launcher_background)
            }
            imgBackgroundBlur.load(track.imageUrl) {
                crossfade(true)
            }
        }

        // ADICIONA ESTAS LINHAS ⬇️
        updatePlayState()
        startSeekBarUpdate()

        // Atualizar botão play/pause
        btnPlayPause.text = "⏸"
    }

    private fun togglePlayPause() {
        if (player?.isPlaying == true) {
            player?.pause()
        } else {
            player?.play()
        }
        updatePlayState()
    }

    private fun nextTrack() {
        val playlist = getCurrentPlaylist()

        if (currentTrackIndex < playlist.tracks.size - 1) {
            currentTrackIndex++
        } else {
            currentTrackIndex = 0 // Volta para a primeira música
        }

        loadTrack(currentPlaylistIndex, currentTrackIndex) // ← Carrega tudo incluindo imagem
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

            // Atualizar botão
            btnPlayPause.text = if (it.isPlaying) "⏸" else "▶"
        }
    }

    private fun triggerEmergency() {
        if (isEmergencyMode) return // Evita múltiplas ativações

        isEmergencyMode = true
        player?.pause()

        // Esconde layout normal e mostra emergência
        normalLayout.visibility = View.GONE
        emergencyLayout.visibility = View.VISIBLE

        // Toca alarme (simulado com beep do sistema por agora)
        // Podes adicionar um MediaPlayer com ficheiro de sirene depois
        Toast.makeText(this, "🚨 ALERTA DE EMERGÊNCIA ATIVADO!", Toast.LENGTH_LONG).show()
    }

    private fun cancelEmergency() {
        isEmergencyMode = false

        // Volta ao layout normal
        emergencyLayout.visibility = View.GONE
        normalLayout.visibility = View.VISIBLE

        // Reinicia música
        player?.play()
        updatePlayState()

        Toast.makeText(this, "✓ Alerta cancelado", Toast.LENGTH_SHORT).show()
    }

    private fun showCall112Confirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirmação de Chamada")
            .setMessage("Tem a certeza que deseja ligar para o 112 (Emergências)?")
            .setPositiveButton("SIM, LIGAR") { _, _ ->
                makeEmergencyCall()
            }
            .setNegativeButton("CANCELAR", null)
            .setCancelable(true)
            .show()
    }

    private fun makeEmergencyCall() {
        // Verificar permissão
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {

            // Pedir permissão
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_CODE
            )
        } else {
            // Fazer chamada
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:112")
            startActivity(callIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CALL_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                makeEmergencyCall()
            } else {
                Toast.makeText(this, "Permissão de chamada negada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_PROXIMITY -> handleProximity(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    private fun handleProximity(event: SensorEvent) {
        if (isEmergencyMode) return // Não processa gestos em modo emergência

        val distance = event.values[0]

        if (distance < event.sensor.maximumRange) {
            // PERTO
            if (!isNear) {
                isNear = true
                gestureStartTime = System.currentTimeMillis()
                tvSensorStatus.text = "● Gesto..."
                tvSensorStatus.setTextColor(0xFFFFAA00.toInt())
            }
        } else {
            // LONGE
            if (isNear) {
                isNear = false
                val gestureDuration = System.currentTimeMillis() - gestureStartTime

                when {
                    gestureDuration < 500 -> {
                        // Gesto curto: Play/Pause
                        togglePlayPause()
                    }
                    gestureDuration in 500..2500 -> {
                        // Gesto longo: Skip
                        nextTrack()
                    }
                }

                tvSensorStatus.text = "● Ativos"
                tvSensorStatus.setTextColor(0xFF00FF00.toInt())
            }
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        if (isEmergencyMode) return // Já está em emergência

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration = sqrt(x * x + y * y + z * z)

        // MODO DEMO: Threshold baixo para demonstração
        // Para produção real, usar 30.0
        if (acceleration > 15.0) {  // ← Valor baixo para demo
            triggerEmergency()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não precisamos disto
    }

    override fun onResume() {
        super.onResume()
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (player?.isPlaying == true) {
            startSeekBarUpdate() // ← ADICIONA
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopSeekBarUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekBarUpdate()
        player?.release()
        sensorManager.unregisterListener(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SELECT_TRACK && resultCode == RESULT_OK) {
            val playlistIndex = data?.getIntExtra("PLAYLIST_INDEX", 0) ?: 0
            val trackIndex = data?.getIntExtra("TRACK_INDEX", 0) ?: 0

            loadTrack(playlistIndex, trackIndex)

            // Ativar botões
            btnPlayPause.isEnabled = true
            btnNext.isEnabled = true
            btnPrevious.isEnabled = true
            btnShuffle.isEnabled = true
            btnRepeat.isEnabled = true
        }
    }
    override fun onStop() {
        super.onStop()
        // Só pausa se a app for realmente minimizada (não quando abre PlaylistActivity)
        if (isFinishing) {
            player?.pause()
        }
    }
    private fun setupPlayerControls() {
        // Play/Pause
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
        // Anterior
        btnPrevious.setOnClickListener {
            previousTrack()
        }

        // Próximo
        btnNext.setOnClickListener {
            nextTrack()
        }

        // Shuffle
        btnShuffle.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            updateShuffleButton()
            Toast.makeText(this,
                if (isShuffleEnabled) "🔀 Shuffle ON" else "🔁 Shuffle OFF",
                Toast.LENGTH_SHORT).show()
        }

// Repeat
        btnRepeat.setOnClickListener {
            isRepeatEnabled = !isRepeatEnabled
            updateRepeatButton()
            Toast.makeText(this,
                if (isRepeatEnabled) "🔁 Repeat ON" else "➡️ Repeat OFF",
                Toast.LENGTH_SHORT).show()
        }

        // SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Atualizar barra de progresso
        startSeekBarUpdate()
    }

    private fun previousTrack() {
        if (currentTrackIndex > 0) {
            currentTrackIndex--
        } else {
            val playlist = getCurrentPlaylist()
            currentTrackIndex = playlist.tracks.size - 1 // Vai para a última música
        }

        loadTrack(currentPlaylistIndex, currentTrackIndex) // ← Carrega tudo incluindo imagem
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
            // Só continua se o player estiver a tocar
            if (player?.isPlaying == true) {
                updateSeekBarHandler.postDelayed(this, 500) // Mudei para 500ms (menos pesado)
            }
        }
    }

    private fun startSeekBarUpdate() {
        updateSeekBarHandler.removeCallbacks(seekBarUpdateRunnable) // Remove callbacks antigos
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
            // Modo Random: escolhe música aleatória de qualquer playlist
            val randomPlaylistIndex = (0 until MusicLibrary.playlists.size).random()
            val randomPlaylist = MusicLibrary.playlists[randomPlaylistIndex]
            val randomTrackIndex = (0 until randomPlaylist.tracks.size).random()

            loadTrack(randomPlaylistIndex, randomTrackIndex)

            Toast.makeText(this, "🔀 Random: ${getCurrentTrack().title}", Toast.LENGTH_SHORT).show()

        } else if (isRepeatEnabled) {
            // Modo Repeat: repete a mesma música
            player?.seekTo(0)
            player?.play()

        } else {
            // Modo Normal: próxima música da playlist atual
            val playlist = getCurrentPlaylist()

            if (currentTrackIndex < playlist.tracks.size - 1) {
                // Ainda há músicas na playlist
                currentTrackIndex++
                loadTrack(currentPlaylistIndex, currentTrackIndex)
            } else {
                // Acabou a playlist, para
                Toast.makeText(this, "✅ Playlist terminada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}