package com.example.musicplayer.core.di

import android.content.Context
import androidx.room.Room
import com.example.musicplayer.core.data.db.MusicDatabase
import com.example.musicplayer.core.data.db.PlaylistDao
import com.example.musicplayer.core.data.db.SongDao
import com.example.musicplayer.core.data.innertube.InnerTubeStreamResolver
import com.example.musicplayer.core.data.innertube.PoTokenProvider
import com.example.musicplayer.core.data.innertube.StreamResolver
import com.example.musicplayer.core.data.innertube.WebViewPoTokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideMusicDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = Room.databaseBuilder(
        context,
        MusicDatabase::class.java,
        "music-player.db",
    )
        .addMigrations(*MusicDatabase.ALL_MIGRATIONS)
        .build()

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao = database.playlistDao()
}

/**
 * Bindings for the streaming layer.
 *
 * [PoTokenProvider] is swappable on purpose. [WebViewPoTokenProvider] spins up a WebView and runs
 * Google's attestation challenge, which costs startup time and only benefits the web client
 * profile; [NoOpPoTokenProvider] declines instantly and leaves playback to the mobile profiles.
 * Switching the binding here is the single lever for that trade-off.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingModule {
    @Binds
    @Singleton
    abstract fun bindPoTokenProvider(impl: WebViewPoTokenProvider): PoTokenProvider

    @Binds
    @Singleton
    abstract fun bindStreamResolver(impl: InnerTubeStreamResolver): StreamResolver
}

