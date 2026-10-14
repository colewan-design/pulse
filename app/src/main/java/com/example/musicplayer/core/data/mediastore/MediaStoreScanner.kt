package com.example.musicplayer.core.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.BaseColumns
import android.provider.MediaStore
import com.example.musicplayer.core.data.db.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scanDeviceAudio(): List<SongEntity> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        // GENRE only exists as a column on Audio.Media from API 30; below that it
        // lives in a separate Audio.Genres table that costs a query per genre,
        // so older devices simply browse without it rather than pay that cost.
        val supportsGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = listOfNotNull(
            BaseColumns._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            pathColumn,
            MediaStore.Audio.Media.GENRE.takeIf { supportsGenreColumn },
        ).toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_TRACK_DURATION_MS.toString())
        val albumArtBaseUri = android.net.Uri.parse("content://media/external/audio/albumart")

        val songs = mutableListOf<SongEntity>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
            val genreIndex = if (supportsGenreColumn) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val relativePath = cursor.getString(pathIndex)?.let { storedPath ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        storedPath.trimEnd('/')
                    } else {
                        File(storedPath).parent.orEmpty()
                    }
                }.orEmpty()

                songs += SongEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getString(titleColumn)?.ifBlank { UNKNOWN_TITLE }.orEmpty(),
                    artist = cursor.getString(artistColumn)?.ifBlank { UNKNOWN_ARTIST }.orEmpty(),
                    album = cursor.getString(albumColumn)?.ifBlank { UNKNOWN_ALBUM }.orEmpty(),
                    albumId = albumId,
                    albumArtUri = albumId.takeIf { it > 0L }?.let {
                        ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    },
                    genre = genreIndex.takeIf { it >= 0 }
                        ?.let(cursor::getString)
                        ?.ifBlank { null }
                        ?: UNKNOWN_GENRE,
                    durationMs = cursor.getLong(durationColumn),
                    trackNumber = cursor.getInt(trackColumn) % 1000,
                    year = cursor.getInt(yearColumn),
                    dateAdded = cursor.getLong(dateAddedColumn),
                    dateModified = cursor.getLong(dateModifiedColumn),
                    relativePath = relativePath,
                )
            }
        }

        songs
    }

    private companion object {
        const val MIN_TRACK_DURATION_MS = 30_000L
        const val UNKNOWN_TITLE = "Unknown title"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
        const val UNKNOWN_GENRE = "Unknown genre"
    }
}

