package net.serverpeon.twitcharchiver.twitch

import com.google.common.net.HttpHeaders
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response

/**
 * Injects the OAuth token into any secure requests to the twitch api.
 *
 * It will not inject the token into insecure HTTP requests for safety.
 */
internal class OAuthInterceptor private constructor(
        private val token: () -> String?,
        private val clientId: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request().let { req ->
            val currentToken = token()
            if (currentToken != null && isSecureTwitchApi(req.url())) {
                req.newBuilder().addHeader(HttpHeaders.AUTHORIZATION, "OAuth $currentToken")
            } else {
                req.newBuilder()
            }.apply {
                addHeader(HttpHeaders.ACCEPT, TWITCH_API_V3_JSON_MEDIATYPE.toString())
                addHeader(TWITCH_CLIENT_ID_HEADER, clientId)
            }.build()
        })
    }

    private fun isSecureTwitchApi(url: HttpUrl): Boolean {
        return url.isHttps && "api.twitch.tv" == url.host()
    }

    companion object {
        private val TWITCH_API_V3_JSON_MEDIATYPE = MediaType.parse("application/vnd.twitchtv.v3+json")
        private val TWITCH_CLIENT_ID_HEADER = "Client-ID"

        fun static(token: String, clientId: String) = OAuthInterceptor({ token }, clientId)

        fun dynamic(token: OAuthToken, clientId: String) = OAuthInterceptor({ token.value }, clientId)
    }
}