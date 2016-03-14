package net.serverpeon.twitcharchiver.twitch

import okhttp3.HttpUrl
import okhttp3.Interceptor
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
                req.newBuilder().addHeader("Authorization", "OAuth ${token.value}")
            } else {
                req.newBuilder()
            }.addHeader("Accept", "Accept: application/vnd.twitchtv.v3+json").build()
        })
    }

    private fun isSecureTwitchApi(url: HttpUrl): Boolean {
        return url.isHttps && "api.twitch.tv".equals(url.host())
    }
}