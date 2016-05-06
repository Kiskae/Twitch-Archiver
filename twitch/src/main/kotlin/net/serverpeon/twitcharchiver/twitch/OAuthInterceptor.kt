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
internal class OAuthInterceptor(private val token: OAuthToken) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request().let { req ->
            if (token.value != null && isSecureTwitchApi(req.url())) {
                req.newBuilder().addHeader(HttpHeaders.AUTHORIZATION, "OAuth ${token.value}")
            } else {
                req.newBuilder()
            }.apply {
                addHeader(HttpHeaders.ACCEPT, TWITCH_API_V3_JSON_MEDIATYPE.toString())
                addHeader(TWITCH_CLIENT_ID_HEADER, "<TODO>") //TODO
            }.build()
        })
    }

    private fun isSecureTwitchApi(url: HttpUrl): Boolean {
        return url.isHttps && "api.twitch.tv".equals(url.host())
    }

    companion object {
        private val TWITCH_API_V3_JSON_MEDIATYPE = MediaType.parse("application/vnd.twitchtv.v3+json")
        private val TWITCH_CLIENT_ID_HEADER = "Client-ID"
    }
}