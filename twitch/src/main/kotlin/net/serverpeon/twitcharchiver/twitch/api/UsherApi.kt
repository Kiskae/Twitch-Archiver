package net.serverpeon.twitcharchiver.twitch.api

import okhttp3.HttpUrl
import java.net.URLEncoder

/**
 * Usher is Twitch's content delivery system, it manages access to the streams
 */
object UsherApi {
    private val USHER_URL = HttpUrl.parse("http://usher.twitch.tv/vod/")
    private const val ALLOW_SOURCE_PARAM = "allow_source"
    private const val ALLOW_SPECTRE_PARAM = "allow_spectre"
    private const val PLAYER_PARAM = "player"
    private const val SIGNATURE_PARAM = "nauthsig"
    private const val TOKEN_PARAM = "nauth"

    /**
     * Constructs the url to the HLS playlist for the given broadcast
     * Requires [InternalApi.AccessResponse] for the given broadcastId
     */
    fun buildResourceUrl(broadcastId: Long, auth: InternalApi.AccessResponse): HttpUrl {
        return USHER_URL.newBuilder()
                .addPathSegment(broadcastId.toString())
                .addQueryParameter(ALLOW_SOURCE_PARAM, "true")
                .addQueryParameter(ALLOW_SPECTRE_PARAM, "true")
                .addQueryParameter(PLAYER_PARAM, "twitch-archiver")
                .addEncodedQueryParameter(SIGNATURE_PARAM, auth.signature.urlEncode())
                .addEncodedQueryParameter(TOKEN_PARAM, auth.token.urlEncode())
                .build()
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}