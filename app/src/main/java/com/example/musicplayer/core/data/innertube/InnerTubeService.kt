package com.example.musicplayer.core.data.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Thin transport over InnerTube. Knows how to shape a request for a given [InnerTubeClient] and
 * nothing about what the responses mean — interpretation lives in [YouTubeMusicRepository] and
 * [InnerTubeStreamResolver].
 *
 * `visitorData` is worth holding onto: YouTube issues it on first contact and expects it back on
 * subsequent calls. Dropping it makes responses less consistent and attestation more likely to
 * trigger, so [visitorData] is cached for the process lifetime.
 */
@Singleton
class InnerTubeService @Inject constructor(
    private val poTokenProvider: PoTokenProvider,
    private val authStore: com.example.musicplayer.core.data.auth.GoogleAuthStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(ContentEncoding) { gzip() }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        defaultRequest {
            header("X-Goog-Api-Format-Version", "1")
            header("Accept-Language", "en-US,en")
        }
    }

    @Volatile
    private var visitorData: String? = null

    internal suspend fun player(
        videoId: String,
        client: InnerTubeClient,
    ): PlayerResponse = withContext(Dispatchers.IO) {
        val poToken = if (client.requiresPoToken) poTokenProvider.poToken(videoId) else null
        val request = buildRequest(client, poToken)
        execute(
            endpoint = InnerTubeEndpoints.PLAYER,
            client = client,
            body = request.copy(
                videoId = videoId,
                contentCheckOk = true,
                racyCheckOk = true,
                // The TV-embedded profile only serves age-gated tracks when it believes it is
                // embedded in a third-party page.
                context = request.context.copy(
                    thirdParty = if (client == InnerTubeClient.TV_EMBEDDED) {
                        InnerTubeRequest.ThirdParty(embedUrl = "https://www.youtube.com/watch?v=$videoId")
                    } else {
                        null
                    },
                ),
            ),
        )
    }

    internal suspend fun search(
        query: String,
        filter: String? = SearchFilter.SONGS,
        client: InnerTubeClient = InnerTubeClient.WEB_REMIX,
    ): BrowseResponse = withContext(Dispatchers.IO) {
        execute(
            endpoint = InnerTubeEndpoints.SEARCH,
            client = client,
            body = buildRequest(client).copy(query = query, params = filter),
        )
    }

    internal suspend fun searchContinuation(
        token: String,
        client: InnerTubeClient = InnerTubeClient.WEB_REMIX,
    ): BrowseResponse = withContext(Dispatchers.IO) {
        execute(
            endpoint = InnerTubeEndpoints.SEARCH,
            client = client,
            body = buildRequest(client),
            continuation = token,
        )
    }

    internal suspend fun browse(
        browseId: String,
        params: String? = null,
        client: InnerTubeClient = InnerTubeClient.WEB_REMIX,
    ): BrowseResponse = withContext(Dispatchers.IO) {
        execute(
            endpoint = InnerTubeEndpoints.BROWSE,
            client = client,
            body = buildRequest(client).copy(browseId = browseId, params = params),
        )
    }

    internal suspend fun searchSuggestions(
        input: String,
        client: InnerTubeClient = InnerTubeClient.WEB_REMIX,
    ): SearchSuggestionsResponse = withContext(Dispatchers.IO) {
        execute(
            endpoint = InnerTubeEndpoints.SEARCH_SUGGESTIONS,
            client = client,
            body = buildRequest(client).copy(input = input),
        )
    }

    /** Fetches the player JS bundle whose descramble functions [JsChallengeSolver] needs. */
    suspend fun fetchPlayerScript(url: String): String = withContext(Dispatchers.IO) {
        httpClient.get(url) {
            header("User-Agent", InnerTubeClient.WEB_REMIX.userAgent)
        }.bodyAsText()
    }

    /** Locates the current player JS URL from the YouTube Music home page. */
    suspend fun fetchPlayerScriptUrl(): String? = withContext(Dispatchers.IO) {
        val html = httpClient.get("https://music.youtube.com/") {
            header("User-Agent", InnerTubeClient.WEB_REMIX.userAgent)
        }.bodyAsText()
        PLAYER_SCRIPT_PATTERN.find(html)?.groupValues?.getOrNull(1)?.let { path ->
            if (path.startsWith("http")) path else "https://music.youtube.com$path"
        }
    }

    private suspend inline fun <reified T> execute(
        endpoint: String,
        client: InnerTubeClient,
        body: InnerTubeRequest,
        continuation: String? = null,
    ): T {
        // Music-app and music-web clients live on the music host; the video clients
        // (ANDROID_VR, TVHTML5, WEB) are only served from www.youtube.com.
        val base = if (client.clientName.endsWith("MUSIC") || client.clientName == "WEB_REMIX") {
            InnerTubeEndpoints.MUSIC_BASE
        } else {
            InnerTubeEndpoints.VIDEO_BASE
        }

        val response = httpClient.post("$base/$endpoint") {
            contentType(ContentType.Application.Json)
            // No `key` parameter: InnerTube accepts these calls unauthenticated, and that is the
            // shape verified working against both hosts. Sending the web API key with a non-web
            // client identity is an inconsistency worth avoiding.
            parameter("prettyPrint", "false")
            continuation?.let { parameter("continuation", it) }

            header("User-Agent", client.userAgent)
            header("X-YouTube-Client-Name", client.clientId.toString())
            header("X-YouTube-Client-Version", client.clientVersion)
            // Origin/Referer only for the browser-identity clients — sending a music.youtube.com
            // origin alongside an Android app User-Agent is a contradiction in the request.
            client.referer?.let {
                header("Referer", it)
                header("Origin", it.trimEnd('/'))
            }
            visitorData?.let { header("X-Goog-Visitor-Id", it) }

            // When the user has signed in, every call goes out authenticated.
            // This is what lifts LOGIN_REQUIRED: the anonymous profiles are
            // refused regardless of headers, because the refusal is about not
            // having an identity attached, not about the request shape.
            //
            // The origin MUST be derived from the host actually being called.
            // SAPISIDHASH is computed over the origin, and Google validates that
            // it matches the request — so a hash minted for music.youtube.com is
            // invalid on the www.youtube.com endpoints. ANDROID_VR is a
            // VIDEO_BASE client and the only profile that has ever resolved a
            // stream, so hardcoding the music origin would break authentication
            // precisely where it matters and look like "signing in didn't help".
            authStore.authHeaders(origin = base.substringBefore("/youtubei"))
                ?.forEach { (name, value) -> header(name, value) }

            setBody(body)
        }

        // visitorData arrives in the response BODY under responseContext, not in an
        // X-Goog-Visitor-Id response header — YouTube does not send that header back, only
        // accepts it on requests. Reading the header captured nothing, so visitorData stayed null
        // on every call and each request looked like a brand-new anonymous session. That is the
        // profile most likely to trip the "Sign in to confirm you're not a bot" gate under any
        // volume, so holding a stable id across a session materially reduces attestation.
        val text = response.bodyAsText()
        runCatching {
            json.parseToJsonElement(text)
                .jsonObject["responseContext"]?.jsonObject
                ?.get("visitorData")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { visitorData = it }

        return json.decodeFromString(text)
    }

    private fun buildRequest(client: InnerTubeClient, poToken: String? = null) = InnerTubeRequest(
        context = InnerTubeRequest.Context(
            client = InnerTubeRequest.Client(
                clientName = client.clientName,
                clientVersion = client.clientVersion,
                osName = client.osName,
                osVersion = client.osVersion,
                androidSdkVersion = client.androidSdkVersion,
                deviceModel = client.deviceModel,
                platform = if (client.androidSdkVersion != null) "MOBILE" else null,
                visitorData = visitorData,
            ),
        ),
        serviceIntegrityDimensions = poToken?.let {
            InnerTubeRequest.ServiceIntegrityDimensions(poToken = it)
        },
    )

    fun close() = httpClient.close()

    private companion object {
        val PLAYER_SCRIPT_PATTERN = Regex("""jsUrl"\s*:\s*"([^"]+base\.js)"""")
    }
}
