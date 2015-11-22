package net.serverpeon.twitcharchiver.twitch

import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Response

/**
 * Injects the OAuth token into any secure requests to the twitch api.
 *
 * It will not inject the token into unsecure HTTP requests for safety.
 */
internal class OAuthInterceptor(private val token: OAuthToken) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request().let { req ->
            if (token.internalToken != null && isSecureTwitchApi(req.httpUrl())) {
                req.newBuilder().addHeader("Authorization", "OAuth ${token.internalToken}")
            } else {
                req.newBuilder()
            }.addHeader("Accept", "Accept: application/vnd.twitchtv.v3+json").build()
        })
    }

    private fun isSecureTwitchApi(url: HttpUrl): Boolean {
        return url.isHttps && "api.twitch.tv".equals(url.host())
    }
}