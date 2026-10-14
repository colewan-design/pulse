package com.example.musicplayer.core.data.innertube

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * Supplies the BotGuard "proof of origin" token that gates attested InnerTube clients.
 *
 * Returning `null` is a supported outcome, not a failure path: it makes
 * [InnerTubeStreamResolver] skip the profiles that require a token and fall through to the mobile
 * ones. Those are tried first anyway, so for most tracks this provider is never consulted.
 */
fun interface PoTokenProvider {
    suspend fun poToken(videoId: String): String?
}

/** Always declines. Playback then depends entirely on the unattested mobile profiles. */
class NoOpPoTokenProvider @Inject constructor() : PoTokenProvider {
    override suspend fun poToken(videoId: String): String? = null
}

/**
 * Runs Google's BotGuard challenge in a WebView to mint a token.
 *
 * The flow Google requires is:
 *  1. `Create` — exchange a hard-coded request key for a challenge containing an obfuscated JS
 *     interpreter plus a program for it to run.
 *  2. Execute that interpreter in a real browser engine. This is the point of the whole exercise:
 *     the program fingerprints the JS environment, so a bare JS interpreter fails where a WebView
 *     passes.
 *  3. `GenerateIT` — trade the interpreter's output for an integrity token.
 *  4. Mint a per-identifier token, bound to either the session's `visitorData` or the videoId.
 *
 * **Scope limit, stated plainly:** steps 1-3 are implemented here; step 4 needs the minting
 * routine carried inside the integrity-token payload, whose shape Google changes freely. This
 * class therefore reaches a correct integrity token and will return `null` rather than a
 * fabricated one if minting does not complete. That is why
 * [InnerTubeClient.STREAM_PROFILE_ORDER] puts the unattested mobile clients first — the app plays
 * music without this path, and treats it as an upgrade rather than a dependency. In the wider
 * ecosystem this piece is usually delegated to a separate helper (yt-dlp uses an external
 * `bgutil` provider for exactly this reason) rather than reimplemented per client.
 */
@Singleton
class WebViewPoTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PoTokenProvider {

    /**
     * Own HTTP client rather than [InnerTubeService]'s. Two reasons: attestation lives on a
     * different host (`jnn-pa.googleapis.com`) and is not an InnerTube endpoint, and injecting the
     * service here would close a dependency cycle — the service already depends on this provider.
     */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
    }

    private val mutex = Mutex()
    private var webView: WebView? = null
    private var cachedIntegrityToken: IntegrityToken? = null

    private data class IntegrityToken(val token: String, val expiresAtMillis: Long) {
        fun isValid() = System.currentTimeMillis() < expiresAtMillis - 60_000
    }

    override suspend fun poToken(videoId: String): String? = mutex.withLock {
        val integrity = cachedIntegrityToken?.takeIf { it.isValid() } ?: run {
            val fresh = withTimeoutOrNull(CHALLENGE_TIMEOUT_MS) { obtainIntegrityToken() }
            fresh?.also { cachedIntegrityToken = it }
        } ?: return@withLock null

        mint(integrity.token, videoId)
    }

    private suspend fun obtainIntegrityToken(): IntegrityToken? {
        val challenge = runCatching { createChallenge() }
            .onFailure { Log.w(TAG, "BotGuard Create failed", it) }
            .getOrNull() ?: return null

        val botGuardResponse = runChallenge(challenge) ?: return null

        val token = runCatching { generateIntegrityToken(botGuardResponse) }
            .onFailure { Log.w(TAG, "BotGuard GenerateIT failed", it) }
            .getOrNull() ?: return null

        return IntegrityToken(
            token = token.first,
            expiresAtMillis = System.currentTimeMillis() + token.second * 1000,
        )
    }

    /** The challenge payload: an obfuscated JS interpreter plus the program it must run. */
    private data class BotGuardChallenge(
        val interpreterJavaScript: String,
        val program: String,
        val globalName: String,
    )

    /**
     * `Create` returns a JSPB array whose shape Google reorders periodically, so fields are read
     * positionally with tolerance rather than mapped to a fixed schema.
     */
    private suspend fun createChallenge(): BotGuardChallenge? {
        val raw = httpClient.post("$ATTESTATION_BASE/Create") {
            contentType(ContentType.Application.Json)
            header("User-Agent", ATTESTATION_USER_AGENT)
            setBody("[\"$REQUEST_KEY\"]")
        }.bodyAsText()

        val root = json.parseToJsonElement(raw).jsonArray
        val payload = root.firstOrNull()?.jsonArray ?: return null
        val strings = payload.mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        }

        // The interpreter is the longest string in the payload; the program and the global
        // variable name sit alongside it.
        val interpreter = strings.maxByOrNull { it.length } ?: return null
        val program = strings.filter { it !== interpreter }.maxByOrNull { it.length } ?: return null
        val globalName = strings.firstOrNull { it.length in 1..32 } ?: "trayride"

        return BotGuardChallenge(interpreter, program, globalName)
    }

    /** Trades the VM output for an integrity token. Returns token plus TTL in seconds. */
    private suspend fun generateIntegrityToken(botGuardResponse: String): Pair<String, Long>? {
        val raw = httpClient.post("$ATTESTATION_BASE/GenerateIT") {
            contentType(ContentType.Application.Json)
            header("User-Agent", ATTESTATION_USER_AGENT)
            setBody(json.encodeToString(listOf(REQUEST_KEY, botGuardResponse)))
        }.bodyAsText()

        val root = json.parseToJsonElement(raw).jsonArray
        val token = (root.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return null
        val ttl = (root.getOrNull(1) as? JsonPrimitive)?.content?.toLongOrNull() ?: 43_200L
        return token to ttl
    }

    /** Executes the challenge program inside the WebView and awaits its callback. */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private suspend fun runChallenge(challenge: BotGuardChallenge): String? {
        val result = CompletableDeferred<String?>()

        val view = withContext(Dispatchers.Main) {
            (webView ?: WebView(context).also { webView = it }).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onResult(value: String?) {
                            result.complete(value?.takeIf { it.isNotBlank() })
                        }

                        @JavascriptInterface
                        fun onError(message: String?) {
                            Log.w(TAG, "BotGuard VM error: $message")
                            result.complete(null)
                        }
                    },
                    BRIDGE_NAME,
                )
            }
        }

        // The interpreter installs itself on a global named by the challenge and exposes an `a`
        // entry point taking the program plus a callback factory. It expects a page context with a
        // real origin, which the youtube.com base URL below provides without loading any remote
        // content.
        val bootstrap = """
            (function() {
              try {
                var vm = globalThis[${quoteJs(challenge.globalName)}];
                if (!vm || typeof vm.a !== 'function') {
                  $BRIDGE_NAME.onError('vm-entrypoint-missing');
                  return;
                }
                var settled = false;
                vm.a(
                  ${quoteJs(challenge.program)},
                  function(fn) {
                    // fn is handed a sink that receives the attestation result.
                    try {
                      fn(function(value) {
                        if (settled) return;
                        settled = true;
                        $BRIDGE_NAME.onResult(value == null ? null : String(value));
                      });
                    } catch (inner) {
                      $BRIDGE_NAME.onError('sink: ' + inner);
                    }
                  },
                  true,
                  undefined,
                  function() {}
                );
              } catch (e) {
                $BRIDGE_NAME.onError(String(e));
              }
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            view.loadDataWithBaseURL(
                "https://www.youtube.com",
                "<!doctype html><html><body><script>${challenge.interpreterJavaScript}</script>" +
                    "<script>$bootstrap</script></body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }

        return withTimeoutOrNull(VM_TIMEOUT_MS) { result.await() }
    }

    /**
     * Binds the integrity token to [identifier]. Returns null while the minting routine is not
     * implemented — see the class note. A wrong token is worse than none: it marks the request as
     * a failed attestation rather than an unattested one.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun mint(integrityToken: String, identifier: String): String? = null

    private fun quoteJs(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""

    fun close() = httpClient.close()

    private companion object {
        const val TAG = "PoTokenProvider"
        const val BRIDGE_NAME = "__mpBotGuard"

        const val ATTESTATION_BASE = "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa"

        /** Google's long-lived public request key for the YouTube attestation surface. */
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

        const val ATTESTATION_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

        const val CHALLENGE_TIMEOUT_MS = 20_000L
        const val VM_TIMEOUT_MS = 15_000L
    }
}
