package com.example.musicplayer.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.musicplayer.core.model.PlaylistSummary
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.core.model.SongSource

@Entity(
    tableName = "songs",
    // `source` is filtered on every rescan and every library query; `remoteId` is the lookup key
    // when reconciling a search result against an already-saved track.
    indices = [Index("source"), Index("remoteId")],
)
data class SongEntity(
    @PrimaryKey val id: Long,
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
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val source: SongSource = SongSource.LOCAL,
    val remoteId: String? = null,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
)

fun SongEntity.asDomainModel(): Song = Song(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    albumArtUri = albumArtUri,
    genre = genre,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    dateAdded = dateAdded,
    dateModified = dateModified,
    relativePath = relativePath,
    isFavorite = isFavorite,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    source = source,
    remoteId = remoteId,
)

fun PlaylistSummaryRow.asDomainModel(): PlaylistSummary = PlaylistSummary(
    id = id,
    name = name,
    createdAt = createdAt,
    songCount = songCount,
)

