package net.serverpeon.twitcharchiver.twitch

import com.google.common.base.Joiner
import okhttp3.HttpUrl
import java.net.URLDecoder

object OAuthHelper {
    private val AUTH_URL: HttpUrl = HttpUrl.parse("https://api.twitch.tv/kraken/oauth2/authorize?response_type=token&force_verify=true")
    private val STATE_PARAM = "state"

    fun authenticationUrl(redirectUri: HttpUrl, scope: List<String>, state: String): HttpUrl {
        return AUTH_URL.newBuilder()
                .addQueryParameter("client_id", TwitchApi.TWITCH_CLIENT_ID)
                .addQueryParameter("redirect_uri", redirectUri.toString())
                .addQueryParameter("scope", Joiner.on(' ').join(scope))
                .addQueryParameter(STATE_PARAM, state)
                .build()
    }

    fun extractAccessToken(redirectedUrl: String, expectedState: String): String {
        val fragment = HttpUrl.parse(redirectedUrl)?.encodedFragment() ?: throw IllegalStateException("Invalid URL")

        // Very basic URL decoder for the fragment
        val response = fragment.splitToSequence('&').associateTo(mutableMapOf<String, String?>()) { part ->
            val parts = part.split('=', limit = 2)
            when (parts.size) {
                1 -> Pair(parts[0], null)
                2 -> Pair(parts[0], URLDecoder.decode(parts[1], "UTF-8"))
                else -> error("limit = 2 doesn't work?")
            }
        }

        val state = checkNotNull(response[STATE_PARAM]) {
            "Failed to include a valid state"
        }
        check(expectedState == state) {
            "Unexpected state; expected: $expectedState, got: $state"
        }

        return checkNotNull(response["access_token"]) {
            "Failed to include an access token"
        }
    }
}