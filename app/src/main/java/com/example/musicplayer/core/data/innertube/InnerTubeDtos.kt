package com.example.musicplayer.core.data.innertube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire models for InnerTube. These cover only the fields this app reads; everything else is
 * dropped by `ignoreUnknownKeys`. Responses are renderer trees — deeply nested, inconsistently
 * populated, and reshaped by Google without notice — so every nested field here is nullable and
 * every consumer must tolerate a null.
 */

@Serializable
internal data class InnerTubeRequest(
    val context: Context,
    val videoId: String? = null,
    val query: String? = null,
    val browseId: String? = null,
    val params: String? = null,
    val continuation: String? = null,
    val playlistId: String? = null,
    val contentCheckOk: Boolean? = null,
    val racyCheckOk: Boolean? = null,
    val input: String? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
) {
    @Serializable
    internal data class Context(
        val client: Client,
        val thirdParty: ThirdParty? = null,
    )

    @Serializable
    internal data class Client(
        val clientName: String,
        val clientVersion: String,
        val hl: String = "en",
        val gl: String = "US",
        val osName: String? = null,
        val osVersion: String? = null,
        val androidSdkVersion: Int? = null,
        val deviceModel: String? = null,
        val platform: String? = null,
        val visitorData: String? = null,
    )

    @Serializable
    internal data class ThirdParty(val embedUrl: String)

    /** Carrier for the BotGuard proof-of-origin token. */
    @Serializable
    internal data class ServiceIntegrityDimensions(val poToken: String)
}

// ---------------------------------------------------------------------------
// player
// ---------------------------------------------------------------------------

@Serializable
internal data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
) {
    @Serializable
    internal data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    )

    @Serializable
    internal data class StreamingData(
        val adaptiveFormats: List<Format> = emptyList(),
        val formats: List<Format> = emptyList(),
        val expiresInSeconds: String? = null,
    )

    /**
     * One encoded stream. Exactly one of [url] / [signatureCipher] is present: mobile clients
     * generally return [url] ready to use, web clients return a ciphered query string that has
     * to be descrambled by the player JS.
     */
    @Serializable
    internal data class Format(
        val itag: Int,
        val url: String? = null,
        val signatureCipher: String? = null,
        val cipher: String? = null,
        val mimeType: String,
        val bitrate: Int = 0,
        val averageBitrate: Int = 0,
        val contentLength: String? = null,
        val audioQuality: String? = null,
        val audioSampleRate: String? = null,
        val audioChannels: Int? = null,
        val loudnessDb: Double? = null,
        val approxDurationMs: String? = null,
    ) {
        val isAudioOnly: Boolean get() = mimeType.startsWith("audio/")
        val effectiveBitrate: Int get() = if (bitrate > 0) bitrate else averageBitrate
    }

    @Serializable
    internal data class VideoDetails(
        val videoId: String,
        val title: String = "",
        val author: String = "",
        val lengthSeconds: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails? = null,
    )
}

@Serializable
internal data class Thumbnails(val thumbnails: List<Thumbnail> = emptyList()) {
    /** Thumbnails come smallest-first; the last entry is the highest resolution offered. */
    fun best(): String? = thumbnails.maxByOrNull { it.width * it.height }?.url
}

@Serializable
internal data class Thumbnail(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

// ---------------------------------------------------------------------------
// search / browse renderer tree
// ---------------------------------------------------------------------------

@Serializable
internal data class BrowseResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    internal data class Contents(
        val tabbedSearchResultsRenderer: TabbedRenderer? = null,
        val singleColumnBrowseResultsRenderer: TabbedRenderer? = null,
        val sectionListRenderer: SectionListRenderer? = null,
    )

    @Serializable
    internal data class ContinuationContents(
        val musicShelfContinuation: MusicShelfRenderer? = null,
        val sectionListContinuation: SectionListRenderer? = null,
    )
}

@Serializable
internal data class TabbedRenderer(val tabs: List<Tab> = emptyList()) {
    @Serializable
    internal data class Tab(val tabRenderer: TabRenderer? = null)

    @Serializable
    internal data class TabRenderer(val content: TabContent? = null)

    @Serializable
    internal data class TabContent(val sectionListRenderer: SectionListRenderer? = null)

    fun sections(): List<SectionListRenderer.Section> =
        tabs.firstNotNullOfOrNull { it.tabRenderer?.content?.sectionListRenderer }?.contents.orEmpty()
}

@Serializable
internal data class SectionListRenderer(
    val contents: List<Section> = emptyList(),
    val continuations: List<Continuation> = emptyList(),
) {
    @Serializable
    internal data class Section(
        val musicShelfRenderer: MusicShelfRenderer? = null,
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
    )
}

@Serializable
internal data class MusicShelfRenderer(
    val title: Runs? = null,
    val contents: List<Item> = emptyList(),
    val continuations: List<Continuation> = emptyList(),
) {
    @Serializable
    internal data class Item(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )
}

@Serializable
internal data class MusicCarouselShelfRenderer(
    val header: Header? = null,
    val contents: List<Item> = emptyList(),
) {
    @Serializable
    internal data class Header(
        val musicCarouselShelfBasicHeaderRenderer: BasicHeader? = null,
    )

    @Serializable
    internal data class BasicHeader(val title: Runs? = null)

    @Serializable
    internal data class Item(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
    )
}

/** The row shape used by search results and album/playlist track lists. */
@Serializable
internal data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn> = emptyList(),
    val fixedColumns: List<FixedColumn> = emptyList(),
    val thumbnail: ThumbnailRenderer? = null,
    val playlistItemData: PlaylistItemData? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
    val overlay: Overlay? = null,
) {
    @Serializable
    internal data class FlexColumn(
        @SerialName("musicResponsiveListItemFlexColumnRenderer")
        val renderer: ColumnRenderer? = null,
    )

    @Serializable
    internal data class FixedColumn(
        @SerialName("musicResponsiveListItemFixedColumnRenderer")
        val renderer: ColumnRenderer? = null,
    )

    @Serializable
    internal data class ColumnRenderer(val text: Runs? = null)

    @Serializable
    internal data class PlaylistItemData(val videoId: String? = null)

    @Serializable
    internal data class Overlay(
        val musicItemThumbnailOverlayRenderer: ThumbnailOverlay? = null,
    )

    @Serializable
    internal data class ThumbnailOverlay(val content: OverlayContent? = null)

    @Serializable
    internal data class OverlayContent(
        val musicPlayButtonRenderer: PlayButton? = null,
    )

    @Serializable
    internal data class PlayButton(val playNavigationEndpoint: NavigationEndpoint? = null)

    /** videoId hides in one of three places depending on which surface produced the row. */
    fun resolveVideoId(): String? = playlistItemData?.videoId
        ?: navigationEndpoint?.watchEndpoint?.videoId
        ?: overlay?.musicItemThumbnailOverlayRenderer?.content
            ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId

    fun flexText(index: Int): String? = flexColumns.getOrNull(index)?.renderer?.text?.text()
}

/** The card shape used by carousels (albums, playlists, artists on the home feed). */
@Serializable
internal data class MusicTwoRowItemRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val thumbnailRenderer: ThumbnailRenderer? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
)

@Serializable
internal data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnail? = null,
) {
    @Serializable
    internal data class MusicThumbnail(val thumbnail: Thumbnails? = null)

    fun best(): String? = musicThumbnailRenderer?.thumbnail?.best()
}

@Serializable
internal data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null,
) {
    @Serializable
    internal data class WatchEndpoint(
        val videoId: String? = null,
        val playlistId: String? = null,
    )

    @Serializable
    internal data class BrowseEndpoint(
        val browseId: String? = null,
        val params: String? = null,
    )
}

@Serializable
internal data class Continuation(
    val nextContinuationData: NextContinuationData? = null,
    val nextRadioContinuationData: NextContinuationData? = null,
) {
    @Serializable
    internal data class NextContinuationData(val continuation: String? = null)

    fun token(): String? =
        nextContinuationData?.continuation ?: nextRadioContinuationData?.continuation
}

/**
 * InnerTube never returns a plain string. Text is always a list of "runs" that have to be
 * concatenated, and the separators (" • ") are themselves runs.
 */
@Serializable
internal data class Runs(val runs: List<Run> = emptyList()) {
    @Serializable
    internal data class Run(
        val text: String = "",
        val navigationEndpoint: NavigationEndpoint? = null,
    )

    fun text(): String = runs.joinToString(separator = "") { it.text }

    /** Drops the " • " separators so the remaining runs are the real values. */
    fun parts(): List<String> = runs
        .map { it.text.trim() }
        .filter { it.isNotEmpty() && it != "•" && it != "·" }

    fun firstBrowseId(): String? = runs.firstNotNullOfOrNull { it.navigationEndpoint?.browseEndpoint?.browseId }
}

@Serializable
internal data class SearchSuggestionsResponse(
    val contents: List<Section> = emptyList(),
) {
    @Serializable
    internal data class Section(
        val searchSuggestionsSectionRenderer: SuggestionsSection? = null,
    )

    @Serializable
    internal data class SuggestionsSection(val contents: List<Suggestion> = emptyList())

    @Serializable
    internal data class Suggestion(
        val searchSuggestionRenderer: SuggestionRenderer? = null,
    )

    @Serializable
    internal data class SuggestionRenderer(val suggestion: Runs? = null)

    fun queries(): List<String> = contents
        .mapNotNull { it.searchSuggestionsSectionRenderer }
        .flatMap { it.contents }
        .mapNotNull { it.searchSuggestionRenderer?.suggestion?.text() }
        .filter { it.isNotBlank() }
}
