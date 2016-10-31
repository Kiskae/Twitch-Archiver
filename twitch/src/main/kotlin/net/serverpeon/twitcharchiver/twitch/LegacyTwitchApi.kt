package net.serverpeon.twitcharchiver.twitch

import hu.akarnokd.rxjava.interop.RxJavaInterop
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.errors.TwitchApiException
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Single
import java.util.*

/**
 * Unified access to all the API calls required to download broadcasts from twitch.
 */
class LegacyTwitchApi(token: OAuthToken) {
    companion object {
        private val log = LoggerFactory.getLogger(LegacyTwitchApi::class.java)
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(OAuthInterceptor.dynamic(token, TwitchApi.TWITCH_CLIENT_ID)).apply {
        System.getProperty("http.debug")?.let { level ->
            val interceptLogger = LoggerFactory.getLogger(OkHttpClient::class.java)
            addInterceptor(
                    HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
                        interceptLogger.trace(it)
                    }).setLevel(HttpLoggingInterceptor.Level.valueOf(level.toUpperCase()))
            )
        }
    }.build()

    private val internalClient = TwitchApi(TwitchApi.createGson(), client)

    /**
     * Retrieves the user associated with the provided [OAuthToken]
     *
     * If the OAuth token is invalid then the optional will be empty
     */
    fun retrieveUser(): Single<Optional<String>> {
        log.trace("retrieveUser()")
        return RxJavaInterop.toV1Single(internalClient.retrieveUser().map {
            Optional.of(it)
        }.toSingle(Optional.empty()))
    }

    /**
     * Retrieves a list of videos for the given channel, if no limit is given then it will
     * provide all the videos available on the channel.
     */
    fun videoList(channelName: String, limit: Int = -1): Observable<KrakenApi.VideoListResponse.Video> {
        return RxJavaInterop.toV1Observable(internalClient.videoList(channelName, limit))
    }

    /**
     * Retrieves the video playlist for the given broadcast.
     *
     * This method makes heavy use of internal APIs and reverse-engineering.
     * If something breaks, check here first.
     */
    @Throws(TwitchApiException::class)
    fun loadPlaylist(broadcastId: String): Single<Playlist> {
        return RxJavaInterop.toV1Single(internalClient.loadPlaylist(broadcastId))
    }
}