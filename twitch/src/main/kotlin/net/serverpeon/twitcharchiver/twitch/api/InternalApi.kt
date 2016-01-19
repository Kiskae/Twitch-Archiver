package net.serverpeon.twitcharchiver.twitch.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Codifies the required requests to INTERNAL Twitch API resources.
 *
 * None of these calls are documented and if something breaks it will probably
 * involve one of these calls or the responses they give.
 */
interface InternalApi {
    /**
     * Retrieves the extended video data, required for the legacy video storage (non-HLS)
     */
    @GET("/api/videos/{broadcastId}")
    fun videoData(@Path("broadcastId") broadcastId: String): Call<LegacyVideoSource>

    /**
     * Requests an access token for the usher HLS distribution system
     * Results can be used to retrieve a playlist through [UsherApi.buildResourceUrl]
     */
    @GET("/api/vods/{broadcastId}/access_token")
    fun requestVodAccess(@Path("broadcastId") broadcastId: Long): Call<AccessResponse>

    data class AccessResponse(@SerializedName("sig") val signature: String,
                              val token: String)

    data class LegacyVideoSource(
            val chunks: LegacyVideoSource.StreamVariations,
            val duration: Long,
            @SerializedName("api_id") val apiId: String
    ) {
        data class StreamVariations(val live: List<VideoPart>)

        data class VideoPart(val url: String, val length: Long, val keep: String?)
    }
}