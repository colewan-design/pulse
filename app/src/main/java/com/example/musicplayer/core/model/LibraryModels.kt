package com.example.musicplayer.core.model

/** Where a [Song]'s audio actually comes from. */
enum class SongSource {
    /** A file on the device, addressed by a `content://` URI from MediaStore. */
    LOCAL,

    /** A YouTube Music track, addressed by [Song.remoteId] and resolved to a URL at play time. */
    YOUTUBE,
}

data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtUri: String?,
    val genre: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val relativePath: String,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val source: SongSource = SongSource.LOCAL,
    /** The 11-character YouTube videoId when [source] is [SongSource.YOUTUBE], else null. */
    val remoteId: String? = null,
) {
    /**
     * Stable identifier handed to Media3.
     *
     * Prefixed so [PlaybackService] can tell a local row from a remote one without consulting the
     * database, and so a future third source can be added without reinterpreting existing ids.
     */
    val mediaId: String
        get() = when (source) {
            SongSource.LOCAL -> "local:$id"
            SongSource.YOUTUBE -> "yt:${remoteId.orEmpty()}"
        }

    val isStreamable: Boolean get() = source != SongSource.LOCAL
}

/**
 * Derives the Room primary key for a remote track from its videoId.
 *
 * Keeping the key a `Long` is what lets YouTube tracks reuse `playlist_songs`, favourites and play
 * counts untouched — those all foreign-key to `songs.id`. The alternative, widening every key to a
 * String, would touch every DAO and migration for no user-visible gain.
 *
 * Collision risk is a 64-bit birthday problem: ~1 in 10 million at 100k saved tracks. The high bit
 * is forced on so remote ids can never collide with a MediaStore `_ID`, which is a small positive
 * integer.
 */
fun youtubeSongId(videoId: String): Long {
    var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    for (char in videoId) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash or Long.MIN_VALUE
}

/** Decodes a Media3 mediaId back into its source and identifier. */
fun parseMediaId(mediaId: String): Pair<SongSource, String>? = when {
    mediaId.startsWith("local:") -> SongSource.LOCAL to mediaId.removePrefix("local:")
    mediaId.startsWith("yt:") -> SongSource.YOUTUBE to mediaId.removePrefix("yt:")
    // Pre-prefix ids were bare MediaStore _IDs; keep them readable so old queues still resolve.
    mediaId.toLongOrNull() != null -> SongSource.LOCAL to mediaId
    else -> null
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val songs: List<Song>,
    val artworkUri: String?,
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val songs: List<Song>,
    val artworkUri: String?,
)

data class Genre(
    val name: String,
    val songCount: Int,
    val songs: List<Song>,
    val artworkUri: String?,
)

data class Folder(
    val path: String,
    val displayName: String,
    val songCount: Int,
    val songs: List<Song>,
    val artworkUri: String?,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
)

/**
 * An album or playlist on YouTube Music that has not been expanded yet.
 *
 * Distinct from [Album], which is derived from songs already in the library. A browse result is
 * just a handle — [browseId] has to be fetched before any tracks exist.
 */
data class RemoteAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: Int,
    val artworkUri: String?,
)

