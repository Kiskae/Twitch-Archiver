package net.serverpeon.twitcharchiver.twitch.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Codifies the requests and responses against the OFFICIAL Twitch API
 *
 * Almost all requests require the user to have a valid OAuth token provided to the
 * [net.serverpeon.twitcharchiver.twitch.OAuthToken] to avoid throttling.
 *
 * Reference: [https://github.com/justintv/Twitch-API]
 */
interface KrakenApi {

    /**
     * [https://github.com/justintv/Twitch-API/blob/master/v3_resources/root.md#get-]
     */
    @GET("/kraken")
    fun authStatus(): Call<AuthResponse>

    data class AuthResponse(val token: AuthResponse.Token?) {
        data class Token(
                val authorization: Token.Authorization?,
                @SerializedName("user_name") val userName: String?,
                val valid: Boolean
        ) {
            data class Authorization(val scopes: List<String>)
        }
    }

    /**
     * [https://github.com/justintv/Twitch-API/blob/master/v3_resources/videos.md#get-channelschannelvideos]
     */
    @GET("/kraken/channels/{channel}/videos")
    fun videoList(@Path("channel") channelName: String,
                  @Query("broadcasts") requestBroadcasts: Boolean = true,
                  @Query("limit") limit: Int? = null,
                  @Query("offset") offset: Int? = null): Call<VideoListResponse>

    data class VideoListResponse(@SerializedName("_total") val totalVideos: Int,
                                 val videos: List<VideoListResponse.Video>) {
        data class Video(val title: String,
                         val views: Long,
                         val length: Duration,
                         @SerializedName("_id") val internalId: String,
                         @SerializedName("recorded_at") val recordedAt: ZonedDateTime)
    }
}