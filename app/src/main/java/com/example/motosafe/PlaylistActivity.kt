package com.example.motosafe

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class PlaylistActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ADICIONA ESTA LINHA ⬇️
        supportActionBar?.hide()

        setContentView(R.layout.activity_playlist)

        // Botão Back
        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        // Carregar playlists
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val container = findViewById<LinearLayout>(R.id.playlistContainer)

        MusicLibrary.playlists.forEachIndexed { playlistIndex, playlist ->
            val playlistCard = createPlaylistCard(playlist, playlistIndex)
            container.addView(playlistCard)
        }
    }

    private fun createPlaylistCard(playlist: Playlist, playlistIndex: Int): CardView {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(0xFF1A1A1A.toInt())
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Header da Playlist
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val emoji = TextView(this).apply {
            text = playlist.emoji
            textSize = 40f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 20
            }
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val playlistName = TextView(this).apply {
            text = playlist.name
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val playlistDesc = TextView(this).apply {
            text = playlist.description
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 4, 0, 0)
        }

        textLayout.addView(playlistName)
        textLayout.addView(playlistDesc)
        headerLayout.addView(emoji)
        headerLayout.addView(textLayout)
        contentLayout.addView(headerLayout)

        // Divisor
        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                topMargin = 16
                bottomMargin = 16
            }
            setBackgroundColor(0xFF333333.toInt())
        }
        contentLayout.addView(divider)

        // Lista de Músicas
        playlist.tracks.forEachIndexed { trackIndex, track ->
            val trackView = createTrackView(track, playlistIndex, trackIndex)
            contentLayout.addView(trackView)
        }

        cardView.addView(contentLayout)

        return cardView
    }

    private fun createTrackView(track: Track, playlistIndex: Int, trackIndex: Int): LinearLayout {
        val trackLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectTrack(playlistIndex, trackIndex)
            }
        }

        val trackNumber = TextView(this).apply {
            text = "${trackIndex + 1}"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            layoutParams = LinearLayout.LayoutParams(40, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
        }

        val trackInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val trackTitle = TextView(this).apply {
            text = track.title
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val trackArtist = TextView(this).apply {
            text = track.artist
            textSize = 13f
            setTextColor(0xFF888888.toInt())
        }

        trackInfo.addView(trackTitle)
        trackInfo.addView(trackArtist)
        trackLayout.addView(trackNumber)
        trackLayout.addView(trackInfo)

        return trackLayout
    }

    private fun selectTrack(playlistIndex: Int, trackIndex: Int) {
        val intent = Intent().apply {
            putExtra("PLAYLIST_INDEX", playlistIndex)
            putExtra("TRACK_INDEX", trackIndex)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}