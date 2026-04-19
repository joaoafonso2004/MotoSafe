package com.example.motosafe

data class Track(
    val title: String,
    val artist: String,
    val url: String,
    val imageUrl: String = ""
)

data class Playlist(
    val name: String,
    val description: String,
    val emoji: String,
    val tracks: List<Track>
)

object MusicLibrary {
    val playlists = listOf(
        Playlist(
            name = "Rock Classics",
            description = "Timeless rock anthems for the road",
            emoji = "🎸",
            tracks = listOf(
                Track(
                    "Highway Ride",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500"
                ),
                Track(
                    "Thunder Road",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
                ),
                Track(
                    "Born to Ride",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500"
                )
            )
        ),
        Playlist(
            name = "Electronic Vibes",
            description = "Energetic beats for night rides",
            emoji = "🎧",
            tracks = listOf(
                Track(
                    "Electric Dreams",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500"
                ),
                Track(
                    "Neon Lights",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                    "https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?w=500"
                ),
                Track(
                    "Digital Highway",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
                    "https://images.unsplash.com/photo-1571330735066-03aaa9429d89?w=500"
                )
            )
        ),
        Playlist(
            name = "Night Ride",
            description = "Chill vibes for midnight cruising",
            emoji = "🌙",
            tracks = listOf(
                Track(
                    "Midnight City",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
                    "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=500"
                ),
                Track(
                    "Starlight Drive",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                    "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=500"
                ),
                Track(
                    "Moonlight Cruise",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                    "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=500"
                )
            )
        ),
        Playlist(
            name = "Adrenaline Rush",
            description = "High-energy tracks for speed demons",
            emoji = "⚡",
            tracks = listOf(
                Track(
                    "Speed Demon",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                    "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=500"
                ),
                Track(
                    "Turbo Boost",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3",
                    "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?w=500"
                ),
                Track(
                    "Nitro Rush",
                    "SoundHelix",
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3",
                    "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=500"
                )
            )
        )
    )
}