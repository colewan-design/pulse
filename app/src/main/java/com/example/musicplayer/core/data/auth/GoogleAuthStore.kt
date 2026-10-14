package com.example.musicplayer.core.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "youtube_auth")

/**
 * Holds the signed-in YouTube session captured by the sign-in WebView.
 *
 * What is stored is the raw `Cookie` header from `music.youtube.com`. That value
 * is equivalent to the account's session — treat it the way you would a password:
 * it never leaves the device, is never logged, and `clear()` is the sign-out.
 *
 * Sending it makes InnerTube requests authenticated, which is what lifts the
 * `LOGIN_REQUIRED` gate the anonymous client profiles hit.
 */
@Singleton
class GoogleAuthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val cookieHeader: Flow<String?> = context.authDataStore.data.map { it[Keys.COOKIE] }

    val isSignedIn: Flow<Boolean> = cookieHeader.map { it.isSapisidBearing() }

    suspend fun save(cookieHeader: String) {
        context.authDataStore.edit { it[Keys.COOKIE] = cookieHeader }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.remove(Keys.COOKIE) }
    }

    /** Null when signed out, so callers can simply omit the auth headers. */
    suspend fun authHeaders(origin: String = MUSIC_ORIGIN): Map<String, String>? {
        val cookie = context.authDataStore.data.first()[Keys.COOKIE] ?: return null
        val sapisid = cookie.cookieValue(SAPISID) ?: cookie.cookieValue(SECURE_3PAPISID) ?: return null

        return mapOf(
            "Cookie" to cookie,
            "Authorization" to sapisidHash(sapisid, origin),
            // Which account in a multi-login session the request applies to.
            "X-Goog-AuthUser" to "0",
        )
    }

    /**
     * Google's SAPISIDHASH scheme: `SAPISIDHASH <ts>_<sha1("<ts> <SAPISID> <origin>")>`.
     *
     * The timestamp is seconds and is part of the hashed input, so the header is
     * recomputed per request rather than cached.
     */
    private fun sapisidHash(sapisid: String, origin: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }

    private fun String?.cookieValue(name: String): String? = this
        ?.split(';')
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    /**
     * A cookie header without SAPISID cannot be signed, so it is not a usable
     * session however long it looks — treat that as signed out.
     */
    private fun String?.isSapisidBearing(): Boolean =
        cookieValue(SAPISID) != null || cookieValue(SECURE_3PAPISID) != null

    private object Keys {
        val COOKIE: Preferences.Key<String> = stringPreferencesKey("cookie_header")
    }

    companion object {
        const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val SAPISID = "SAPISID"
        private const val SECURE_3PAPISID = "__Secure-3PAPISID"
    }
}
