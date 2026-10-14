package com.example.musicplayer.core.data.innertube

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the two JavaScript challenges YouTube puts in front of web-client stream URLs.
 *
 * 1. **Signature cipher.** Web `player` responses return `s=<scrambled>` instead of a signed URL.
 *    The descramble routine lives in YouTube's `base.js`, changes shape regularly, and has to be
 *    lifted out and executed.
 * 2. **The `n` parameter.** Left untransformed, YouTube throttles the download to roughly dial-up
 *    speed — the stream plays, then starves. The fix is another function from the same bundle.
 *
 * **This class is the maintenance surface of the whole feature.** The regexes below track
 * identifiers in minified Google output; when a player update renames them, extraction fails and
 * web-client playback stops until they are updated. The mobile profiles in
 * [InnerTubeClient.STREAM_PROFILE_ORDER] are tried first precisely so this path stays cold.
 *
 * Execution uses a WebView rather than bundling a JS interpreter: the app needs a WebView for
 * [PoTokenProvider] anyway, so this avoids a second multi-megabyte dependency.
 */
@Singleton
class JsChallengeSolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: InnerTubeService,
) {
    private val initMutex = Mutex()
    private var webView: WebView? = null
    private var functionsReady = false
    private var sigReady = false
    private var nReady = false

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureReady(): Boolean = initMutex.withLock {
        if (functionsReady) return@withLock true

        val scriptUrl = service.fetchPlayerScriptUrl() ?: run {
            Log.w(TAG, "Could not locate base.js")
            return@withLock false
        }
        val playerJs = runCatching { service.fetchPlayerScript(scriptUrl) }.getOrNull() ?: return@withLock false

        val sigSource = extractSignatureFunction(playerJs)
        val nSource = extractNFunction(playerJs)
        if (sigSource == null && nSource == null) {
            Log.w(TAG, "Neither challenge function could be extracted from $scriptUrl")
            return@withLock false
        }

        // Both functions are installed as part of a real document rather than injected with
        // evaluateJavascript afterwards. A WebView that has never loaded anything has no JS
        // context for an eval to land in, and definitions injected into one document do not
        // survive a navigation — carrying them in the page itself avoids both problems.
        val document = buildString {
            append("<!doctype html><html><body><script>")
            sigSource?.let { append(it).append('\n') }
            nSource?.let { append(it).append('\n') }
            append("</script></body></html>")
        }

        val loaded = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            val view = (webView ?: WebView(context).also { webView = it }).apply {
                settings.javaScriptEnabled = true
                // The document is self-contained; nothing should be fetched while it runs.
                settings.blockNetworkLoads = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!loaded.isCompleted) loaded.complete(Unit)
                    }
                }
            }
            view.loadDataWithBaseURL("https://music.youtube.com", document, "text/html", "utf-8", null)
        }

        if (withTimeoutOrNull(EVAL_TIMEOUT_MS) { loaded.await() } == null) {
            Log.w(TAG, "WebView never finished loading the challenge document")
            return@withLock false
        }

        // Tracked separately: extracting one function but not the other is common after a player
        // update, and a throttled stream still plays while an undeciphered one does not.
        sigReady = sigSource != null
        nReady = nSource != null
        functionsReady = true
        true
    }

    /**
     * Converts a `signatureCipher` query string into a playable URL.
     *
     * The payload looks like `s=<scrambled>&sp=sig&url=<encoded>`: descramble `s`, then append it
     * to `url` under the parameter named by `sp`.
     */
    suspend fun decipher(signatureCipher: String): String? {
        if (!ensureReady() || !sigReady) return null
        val view = webView ?: return null

        val params = signatureCipher.split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
            }
            .toMap()

        val scrambled = params["s"]?.decodeURLQueryComponent() ?: return null
        val signatureParam = params["sp"]?.decodeURLQueryComponent() ?: "signature"
        val baseUrl = params["url"]?.decodeURLQueryComponent() ?: return null

        val descrambled = evaluate(view, "$SIG_FN_NAME(${quote(scrambled)})") ?: return null
        val signed = "$baseUrl&$signatureParam=${descrambled.encodeURLParameter()}"
        return applyThrottleFix(signed)
    }

    /**
     * Rewrites the `n` query parameter so YouTube serves the stream at full speed.
     *
     * Returns [url] unchanged when the transform is unavailable — a throttled stream still plays,
     * it just buffers badly, which is a better failure than no playback at all.
     */
    suspend fun applyThrottleFix(url: String): String {
        val nParam = runCatching { Url(url).parameters["n"] }.getOrNull() ?: return url
        if (!ensureReady() || !nReady) return url
        val view = webView ?: return url

        val transformed = evaluate(view, "$N_FN_NAME(${quote(nParam)})") ?: return url
        if (transformed.isBlank() || transformed == nParam) return url
        return url.replace("n=${nParam.encodeURLParameter()}", "n=${transformed.encodeURLParameter()}")
            .replace("n=$nParam", "n=${transformed.encodeURLParameter()}")
    }

    private suspend fun evaluate(view: WebView, script: String): String? = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    view.evaluateJavascript(script) { raw ->
                        // evaluateJavascript hands back a JSON literal: "\"value\"" or "null".
                        val unwrapped = when {
                            raw == null || raw == "null" -> null
                            raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"') ->
                                raw.substring(1, raw.length - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                            else -> raw
                        }
                        continuation.resume(unwrapped)
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    /** Forces a re-fetch of base.js, e.g. after playback starts failing across the board. */
    suspend fun invalidate() = initMutex.withLock {
        functionsReady = false
        sigReady = false
        nReady = false
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // -----------------------------------------------------------------------
    // base.js extraction — the fragile part
    // -----------------------------------------------------------------------

    /**
     * Lifts the signature descramble function plus the helper object it delegates to, and rebinds
     * it as [SIG_FN_NAME].
     *
     * Minified shape is roughly:
     * ```
     * Xy=function(a){a=a.split("");Zt.rC(a,3);Zt.pK(a,47);return a.join("")}
     * var Zt={rC:function(a,b){a.splice(0,b)}, pK:function(a){a.reverse()}, ...}
     * ```
     */
    private fun extractSignatureFunction(playerJs: String): String? {
        val body = SIG_FUNCTION_PATTERNS.firstNotNullOfOrNull { it.find(playerJs) } ?: return null
        val functionSource = body.value
        val helperName = HELPER_OBJECT_REFERENCE.find(functionSource)?.groupValues?.getOrNull(1)
        val helperSource = helperName?.let { extractHelperObject(playerJs, it) }

        return buildString {
            helperSource?.let { append("var ").append(it).append(';') }
            append("function ").append(SIG_FN_NAME)
            append(functionSource.substring(functionSource.indexOf('(')))
        }
    }

    private fun extractHelperObject(playerJs: String, name: String): String? {
        val escaped = Regex.escape(name)
        val match = Regex("""var\s+$escaped\s*=\s*\{""").find(playerJs) ?: return null
        val openBrace = playerJs.indexOf('{', match.range.first)
        val close = matchBrace(playerJs, openBrace) ?: return null
        return name + "=" + playerJs.substring(openBrace, close + 1)
    }

    /** Lifts the `n`-parameter transform and rebinds it as [N_FN_NAME]. */
    private fun extractNFunction(playerJs: String): String? {
        val match = N_FUNCTION_PATTERNS.firstNotNullOfOrNull { it.find(playerJs) } ?: return null
        val start = playerJs.indexOf('{', match.range.first)
        val end = matchBrace(playerJs, start) ?: return null
        val args = N_FUNCTION_ARGS.find(match.value)?.groupValues?.getOrNull(1) ?: "a"
        return "function $N_FN_NAME($args)${playerJs.substring(start, end + 1)}"
    }

    /** Finds the brace matching the one at [openIndex], respecting strings and escapes. */
    private fun matchBrace(source: String, openIndex: Int): Int? {
        var depth = 0
        var index = openIndex
        var quoteChar: Char? = null
        while (index < source.length) {
            val char = source[index]
            when {
                quoteChar != null -> {
                    if (char == '\\') index++
                    else if (char == quoteChar) quoteChar = null
                }
                char == '"' || char == '\'' || char == '`' -> quoteChar = char
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private companion object {
        const val TAG = "JsChallengeSolver"
        const val SIG_FN_NAME = "__mpDescrambleSig"
        const val N_FN_NAME = "__mpTransformN"
        const val EVAL_TIMEOUT_MS = 10_000L

        /** Several shapes, newest first — Google has shipped all of these. */
        val SIG_FUNCTION_PATTERNS = listOf(
            Regex("""[a-zA-Z_$][\w$]*\s*=\s*function\s*\(\s*[a-zA-Z_$][\w$]*\s*\)\s*\{\s*[a-zA-Z_$][\w$]*\s*=\s*[a-zA-Z_$][\w$]*\.split\(\s*(?:""|'')\s*\)[\s\S]{0,600}?return\s+[a-zA-Z_$][\w$]*\.join\(\s*(?:""|'')\s*\)\s*\}"""),
            Regex("""function\s+[a-zA-Z_$][\w$]*\s*\(\s*[a-zA-Z_$][\w$]*\s*\)\s*\{\s*[a-zA-Z_$][\w$]*\s*=\s*[a-zA-Z_$][\w$]*\.split\(\s*(?:""|'')\s*\)[\s\S]{0,600}?return\s+[a-zA-Z_$][\w$]*\.join\(\s*(?:""|'')\s*\)\s*\}"""),
        )

        /** Matches `Zt.rC(a,3)` inside the descramble body to recover the helper object name. */
        val HELPER_OBJECT_REFERENCE = Regex(""";\s*([a-zA-Z_$][\w$]*)\s*\.\s*[a-zA-Z_$][\w$]*\s*\(""")

        val N_FUNCTION_PATTERNS = listOf(
            Regex("""[a-zA-Z_$][\w$]*\s*=\s*function\s*\(\s*([a-zA-Z_$][\w$]*)\s*\)\s*\{\s*var\s+[\s\S]{0,100}?\.split\([\s\S]{0,8000}?return\s+[a-zA-Z_$][\w$]*\.join\(\s*(?:""|'')\s*\)\s*\}"""),
            Regex("""function\s*\(\s*([a-zA-Z_$][\w$]*)\s*\)\s*\{\s*var\s+[a-zA-Z_$][\w$]*\s*=\s*[a-zA-Z_$][\w$]*\.split\(\s*(?:""|'')\s*\)[\s\S]{0,8000}?enhanced_except"""),
        )

        val N_FUNCTION_ARGS = Regex("""function\s*(?:[a-zA-Z_$][\w$]*)?\s*\(\s*([^)]*)\)""")
    }
}
