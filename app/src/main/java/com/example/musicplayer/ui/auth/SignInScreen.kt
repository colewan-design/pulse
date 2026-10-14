package com.example.musicplayer.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.musicplayer.ui.theme.PulseTextMuted

/**
 * Signs the user into their own Google account so InnerTube requests can be
 * authenticated.
 *
 * The WebView runs with a desktop Chrome user-agent. Google refuses account
 * sign-in from an identifiable embedded WebView ("This browser or app may not be
 * secure"), and the stock Android WebView UA is identifiable. This is a
 * well-known limitation of the approach rather than a trick specific to this app.
 *
 * Nothing is captured until the session actually carries SAPISID — the cookie the
 * request signature is derived from.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(
    onCancel: () -> Unit,
    onSignedIn: (cookieHeader: String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var pasteMode by remember { mutableStateOf(false) }
    BackHandler(onBack = onCancel)

    if (pasteMode) {
        PasteCookieScreen(
            onCancel = { pasteMode = false },
            onSignedIn = onSignedIn,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cancel sign-in")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Sign in to YouTube Music", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your session stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PulseTextMuted,
                )
            }
            TextButton(onClick = { pasteMode = true }) { Text("Paste cookie") }
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                }
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = DESKTOP_USER_AGENT
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            if (url == null) return
                            // Only harvest once redirected back to the music host:
                            // the session is not complete until then.
                            if (!url.contains("music.youtube.com")) return

                            val cookies = CookieManager.getInstance()
                                .getCookie("https://music.youtube.com")
                                .orEmpty()
                            if (cookies.contains("SAPISID")) {
                                CookieManager.getInstance().flush()
                                onSignedIn(cookies)
                            }
                        }
                    }
                    loadUrl(SIGN_IN_URL)
                }
            },
        )
    }
}

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Safari/537.36"

/**
 * Fallback for when Google refuses the embedded WebView with "This browser or app
 * may not be secure" — which is its normal behaviour at the credential step, not
 * an error on our side. Google fingerprints far more than the user-agent, so
 * spoofing cannot be relied on.
 *
 * Pasting the session from a browser where the user has *already* signed in
 * sidesteps the check entirely, and has the side benefit that no password is
 * ever typed into this app.
 */
@Composable
private fun PasteCookieScreen(
    onCancel: () -> Unit,
    onSignedIn: (cookieHeader: String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val valid = value.contains("SAPISID")
    BackHandler(onBack = onCancel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Paste session cookie", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            text = "On a computer, sign in to music.youtube.com in Chrome. Open DevTools " +
                "(F12) → Network, click any request to music.youtube.com, and copy the full " +
                "value of its Cookie request header. Paste it below.",
            style = MaterialTheme.typography.bodyMedium,
            color = PulseTextMuted,
        )
        Text(
            text = "This is your account session — treat it like a password. It is stored " +
                "only on this device, and Sign out erases it.",
            style = MaterialTheme.typography.bodySmall,
            color = PulseTextMuted,
        )

        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            label = { Text("Cookie header") },
            placeholder = { Text("VISITOR_INFO1_LIVE=...; SAPISID=...; ...") },
        )

        if (value.isNotBlank() && !valid) {
            Text(
                text = "That value has no SAPISID in it, so it cannot sign requests. " +
                    "Make sure you copied the whole Cookie header.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { onSignedIn(value.trim()) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save session")
        }
    }
}
