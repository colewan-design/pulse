package com.example.musicplayer.core.data.repository

import com.example.musicplayer.core.data.db.SongDao
import com.example.musicplayer.core.data.db.SongEntity
import com.example.musicplayer.core.data.db.asDomainModel
import com.example.musicplayer.core.data.innertube.InnerTubeService
import com.example.musicplayer.core.data.innertube.MusicResponsiveListItemRenderer
import com.example.musicplayer.core.data.innertube.MusicTwoRowItemRenderer
import com.example.musicplayer.core.data.innertube.SearchFilter
import com.example.musicplayer.core.model.RemoteAlbum
import com.example.musicplayer.core.model.Song
import com.example.musicplayer.core.model.SongSource
import com.example.musicplayer.core.model.youtubeSongId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Search and browse over YouTube Music, mapped into the app's own [Song] model so the existing
 * library UI, queue and playlist code can consume streamed tracks without knowing they are remote.
 *
 * Tracks are only written to Room once the user acts on them ([save]). Caching every search result
 * would fill the library with rows nobody asked for, and `songs` is what the Library screen reads.
 */
@Singleton
class YouTubeMusicRepository @Inject constructor(
    private val service: InnerTubeService,
    private val songDao: SongDao,
) {

    fun observeSavedStreams(): Flow<List<Song>> =
        songDao.observeBySource(SongSource.YOUTUBE).map { rows -> rows.map { it.asDomainModel() } }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val response = service.search(query, SearchFilter.SONGS)
        response.contents?.tabbedSearchResultsRenderer?.sections()
            .orEmpty()
            .flatMap { section -> section.musicShelfRenderer?.contents.orEmpty() }
            .mapNotNull { it.musicResponsiveListItemRenderer?.toSong() }
            .distinctBy { it.remoteId }
    }

    suspend fun searchAlbums(query: String): List<RemoteAlbum> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val response = service.search(query, SearchFilter.ALBUMS)
        response.contents?.tabbedSearchResultsRenderer?.sections()
            .orEmpty()
            .flatMap { section ->
                section.musicShelfRenderer?.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer?.toRemoteAlbum() } +
                    section.musicCarouselShelfRenderer?.contents.orEmpty()
                        .mapNotNull { it.musicTwoRowItemRenderer?.toRemoteAlbum() }
            }
            .distinctBy { it.browseId }
    }

    /** Loads an album or playlist's track list. [browseId] comes from a [RemoteAlbum]. */
    suspend fun albumTracks(browseId: String): List<Song> = withContext(Dispatchers.IO) {
        val response = service.browse(browseId)
        val sections = response.contents?.singleColumnBrowseResultsRenderer?.sections()
            ?: response.contents?.sectionListRenderer?.contents
            ?: emptyList()

        sections
            .flatMap { it.musicShelfRenderer?.contents.orEmpty() }
            .mapNotNull { it.musicResponsiveListItemRenderer?.toSong() }
            .mapIndexed { index, song -> song.copy(trackNumber = index + 1) }
    }

    suspend fun suggestions(input: String): List<String> = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext emptyList()
        runCatching { service.searchSuggestions(input).queries() }.getOrDefault(emptyList())
    }

    /** Persists a streamed track so it can be favourited, queued or added to a playlist. */
    suspend fun save(song: Song): Song = withContext(Dispatchers.IO) {
        require(song.source == SongSource.YOUTUBE) { "save() is for streamed tracks" }
        val remoteId = requireNotNull(song.remoteId) { "streamed track without a videoId" }

        // Preserve any user state already attached to this track.
        val existing = songDao.getByRemoteId(remoteId)
        val entity = song.toEntity().copy(
            isFavorite = existing?.isFavorite ?: song.isFavorite,
            playCount = existing?.playCount ?: song.playCount,
            lastPlayedAt = existing?.lastPlayedAt ?: song.lastPlayedAt,
        )
        songDao.upsertSongs(listOf(entity))
        entity.asDomainModel()
    }

    suspend fun saveAll(songs: List<Song>): List<Song> = songs.map { save(it) }

    /** Drops streamed rows the user never engaged with, so the cache does not grow forever. */
    suspend fun pruneUnreferenced() = withContext(Dispatchers.IO) {
        songDao.pruneUnreferencedRemoteSongs()
    }

    // -----------------------------------------------------------------------
    // renderer → domain
    // -----------------------------------------------------------------------

    /**
     * Maps a search/track row to a [Song].
     *
     * Column layout is positional and varies by surface: a song row is
     * `title | artist · album · duration`, while an album track row omits the album. Rather than
     * trust the index, the trailing columns are parsed by shape — a `m:ss` token is the duration,
     * and a bare 4-digit token is a year.
     */
    private fun MusicResponsiveListItemRenderer.toSong(): Song? {
        val videoId = resolveVideoId() ?: return null
        val title = flexText(0)?.takeIf { it.isNotBlank() } ?: return null

        val detailParts = flexColumns.drop(1)
            .mapNotNull { it.renderer?.text?.parts() }
            .flatten()
        val fixedParts = fixedColumns.mapNotNull { it.renderer?.text?.text() }
        val allParts = detailParts + fixedParts

        val durationMs = allParts.firstNotNullOfOrNull { parseDuration(it) } ?: 0L
        val descriptive = allParts.filter { parseDuration(it) == null && !it.isViewCount() }

        val artist = descriptive.firstOrNull().orEmpty()
        val album = descriptive.drop(1).firstOrNull { it.toIntOrNull() == null }.orEmpty()

        return Song(
            id = youtubeSongId(videoId),
            uri = "",
            title = title,
            artist = artist,
            album = album,
            albumId = album.lowercase().hashCode().toLong(),
            albumArtUri = thumbnail?.best(),
            genre = "",
            durationMs = durationMs,
            trackNumber = 0,
            year = descriptive.firstNotNullOfOrNull { part ->
                part.toIntOrNull()?.takeIf { it in 1900..2100 }
            } ?: 0,
            dateAdded = System.currentTimeMillis() / 1000,
            dateModified = System.currentTimeMillis() / 1000,
            relativePath = "",
            isFavorite = false,
            playCount = 0,
            lastPlayedAt = null,
            source = SongSource.YOUTUBE,
            remoteId = videoId,
        )
    }

    private fun MusicResponsiveListItemRenderer.toRemoteAlbum(): RemoteAlbum? {
        val browseId = navigationEndpoint?.browseEndpoint?.browseId ?: return null
        val title = flexText(0)?.takeIf { it.isNotBlank() } ?: return null
        val parts = flexColumns.drop(1).mapNotNull { it.renderer?.text?.parts() }.flatten()
        return RemoteAlbum(
            browseId = browseId,
            title = title,
            artist = parts.firstOrNull { it.toIntOrNull() == null && !it.isAlbumTypeLabel() }.orEmpty(),
            year = parts.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { y -> y in 1900..2100 } } ?: 0,
            artworkUri = thumbnail?.best(),
        )
    }

    private fun MusicTwoRowItemRenderer.toRemoteAlbum(): RemoteAlbum? {
        val browseId = navigationEndpoint?.browseEndpoint?.browseId ?: return null
        val name = title?.text()?.takeIf { it.isNotBlank() } ?: return null
        val parts = subtitle?.parts().orEmpty()
        return RemoteAlbum(
            browseId = browseId,
            title = name,
            artist = parts.firstOrNull { it.toIntOrNull() == null && !it.isAlbumTypeLabel() }.orEmpty(),
            year = parts.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { y -> y in 1900..2100 } } ?: 0,
            artworkUri = thumbnailRenderer?.best(),
        )
    }

    /** Parses `m:ss` / `h:mm:ss` into milliseconds, or null when the token is not a duration. */
    private fun parseDuration(text: String): Long? {
        val segments = text.trim().split(':')
        if (segments.size < 2 || segments.size > 3) return null
        val numbers = segments.map { it.toIntOrNull() ?: return null }
        val seconds = when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        }
        return seconds * 1000L
    }

    private fun String.isViewCount(): Boolean =
        contains("play", ignoreCase = true) || contains("view", ignoreCase = true)

    private fun String.isAlbumTypeLabel(): Boolean =
        equals("Album", ignoreCase = true) ||
            equals("Single", ignoreCase = true) ||
            equals("EP", ignoreCase = true) ||
            equals("Playlist", ignoreCase = true)
}

private fun Song.toEntity(): SongEntity = SongEntity(
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
