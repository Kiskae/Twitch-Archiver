package net.serverpeon.twitcharchiver.twitch

import com.google.common.collect.ImmutableList
import com.google.common.io.Resources
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.OkHttpClient
import net.serverpeon.twitcharchiver.hls.HlsParser
import net.serverpeon.twitcharchiver.hls.TagRepository
import net.serverpeon.twitcharchiver.twitch.api.InternalApi
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.api.UsherApi
import net.serverpeon.twitcharchiver.twitch.errors.RestrictedAccessException
import net.serverpeon.twitcharchiver.twitch.errors.TwitchApiException
import net.serverpeon.twitcharchiver.twitch.errors.UnrecognizedVodFormatException
import net.serverpeon.twitcharchiver.twitch.json.DateTimeConverter
import net.serverpeon.twitcharchiver.twitch.json.DurationConverter
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import org.slf4j.LoggerFactory
import retrofit.GsonConverterFactory
import retrofit.Retrofit
import rx.Observable
import rx.Single
import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

/**
 * Unified access to all the API calls required to download broadcasts from twitch.
 */
class TwitchApi(token: OAuthToken) {
    companion object {
        private val TWITCH_API_URL = HttpUrl.parse("https://api.twitch.tv/")
        private const val BROADCASTS_PER_REQUEST: Int = 100
        private const val TOP_QUALITY_STREAM: String = "chunked"
        private val log = LoggerFactory.getLogger(TwitchApi::class.java)
    }

    private val gson: Gson = GsonBuilder().let {
        it.registerTypeAdapter(Duration::class.java, DurationConverter)
        it.registerTypeAdapter(ZonedDateTime::class.java, DateTimeConverter)
        it.create()
    }

    private val client: OkHttpClient = OkHttpClient().apply {
        interceptors().add(OAuthInterceptor(token))
        interceptors().add(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): com.squareup.okhttp.Response? {
                log.info("Begin Request")
                return chain.proceed(chain.request())
            }
        })
    }

    private val retrofit: Retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .baseUrl(TWITCH_API_URL)
            .build()

    private val krakenApi: KrakenApi by lazy { retrofit.create(KrakenApi::class.java) }
    private val internalApi: InternalApi by lazy { retrofit.create(InternalApi::class.java) }

    private fun Throwable.isNotRetrofitCancelled(): Boolean {
        // If Call.cancel() is called, an IOException with the message "Canceled" is thrown
        return !"Canceled".equals(message)
    }

    /**
     * Retrieves the user associated with the provided [OAuthToken]
     *
     * If the OAuth token is invalid then the optional will be empty
     */
    @Throws(TwitchApiException::class, IOException::class)
    fun retrieveUser(): Single<Optional<String>> {
        log.trace("retrieveUser()")
        return krakenApi.authStatus()
                .toRx()
                .doOnError {
                    if (it.isNotRetrofitCancelled())
                        log.error("retrieveUser() failed", it)
                }
                .map { user ->
                    if (user.token?.valid ?: false) {
                        Optional.of(user.token!!.userName!!)
                    } else {
                        Optional.empty() //Is a bad token an error?
                    }
                }
    }

    /**
     * Retrieves a list of videos for the given channel, if no limit is given then it will
     * provide all the videos available on the channel.
     */
    @Throws(IOException::class)
    fun videoList(channelName: String, limit: Int = -1): Observable<KrakenApi.VideoListResponse.Video> {
        require(limit != 0) { "Limit must be higher than 0" }
        log.trace("videoList({}, {})", channelName, limit)

        if (limit > 0 && limit <= BROADCASTS_PER_REQUEST) {
            return krakenApi.videoList(channelName, limit = limit)
                    .toRx()
                    .toObservable()
                    .flatMapIterable { it.videos }
        } else {
            return krakenApi.videoList(channelName)
                    .toRx()
                    .doOnError {
                        if (it.isNotRetrofitCancelled())
                            log.error("videoList({}, {}) failed", channelName, limit, it)
                    }
                    .toObservable()
                    .flatMap { response ->
                        val totalVideos = if (limit < 0) response.totalVideos else Math.min(limit, response.totalVideos)

                        val nextCalls: MutableList<Observable<KrakenApi.VideoListResponse.Video>> = LinkedList()
                        for (nextOffset in IntRange(BROADCASTS_PER_REQUEST, totalVideos).step(BROADCASTS_PER_REQUEST)) {
                            nextCalls.add(krakenApi.videoList(
                                    channelName,
                                    limit = Math.min(BROADCASTS_PER_REQUEST, totalVideos - nextOffset),
                                    offset = nextOffset
                            ).toRx().toObservable().flatMapIterable { it.videos })
                        }

                        Observable.concat(
                                Observable.from(response.videos), //We start with the videos we already have
                                Observable.concat(
                                        Observable.from(nextCalls) // Then we perform the remaining calls in order
                                )
                        )
                    }
        }
    }

    /**
     * Retrieves the video playlist for the given broadcast.
     *
     * This method makes heavy use of internal APIs and reverse-engineering.
     * If something breaks, check here first.
     */
    @Throws(TwitchApiException::class, IOException::class)
    fun loadPlaylist(broadcastId: String): Single<Playlist> {
        log.trace("loadPlaylist({})", broadcastId)

        if (broadcastId.startsWith('a')) {
            //Old style video storage
            return retrieveLegacyPlaylist(broadcastId)
        } else if (broadcastId.startsWith('v')) {
            //HLS style video storage
            return retrieveHlsPlaylist(broadcastId.substring(1).toLong())
        } else {
            throw UnrecognizedVodFormatException(broadcastId)
        }
    }

    private fun retrieveHlsPlaylist(broadcastId: Long): Single<Playlist> {
        log.trace("retrieveHlsPlaylist({})", broadcastId)

        return internalApi.requestVodAccess(broadcastId)
                .toRx()
                .doOnError {
                    if (it.isNotRetrofitCancelled())
                        log.error("retrieveHlsPlaylist({}) failed", broadcastId, it)
                }
                .map { auth ->
                    val url = UsherApi.buildResourceUrl(broadcastId, auth)
                    log.debug("Loading variant playlist for {}: {}", broadcastId, url)
                    try {
                        val variants = HlsParser.parseVariantPlaylist(
                                url.uri(),
                                Resources.asCharSource(url.url(), Charsets.UTF_8),
                                TagRepository.DEFAULT
                        )
                        log.debug("Available qualities: {}", variants.map { Pair(it.info.video, it.info.bandwidth) })
                        val bestQualityStream = variants.sortedByDescending { it.info.bandwidth }.first()

                        check(bestQualityStream.info.video?.equals(TOP_QUALITY_STREAM) ?: false) {
                            "Best quality stream is not '$TOP_QUALITY_STREAM', something is wrong"
                        }

                        log.debug("Loading stream playlist for {}: {}", broadcastId, bestQualityStream.uri)
                        Playlist.loadHlsPlaylist(bestQualityStream)
                    } catch (ex: IOException) {
                        val restrictedBitrates = gson.fromJson(auth.token, TwitchToken::class.java)
                                .chansub
                                ?.restricted_bitrates ?: ImmutableList.of()

                        // Heuristics to detect unavailable vod due to
                        // missing channel subscription
                        if (restrictedBitrates.isNotEmpty() && ex.message?.contains("response code: 403") ?: true) {
                            throw RestrictedAccessException(broadcastId, ex)
                        } else {
                            throw ex
                        }
                    }
                }
    }

    private data class TwitchToken(val chansub: TwitchToken.ChanSubDetails?) {
        data class ChanSubDetails(val restricted_bitrates: List<String>?, val privileged: Boolean?)
    }

    private fun retrieveLegacyPlaylist(broadcastId: String): Single<Playlist> {
        log.trace("retrieveLegacyPlaylist({})", broadcastId)

        return internalApi.videoData(broadcastId)
                .toRx()
                .doOnError {
                    if (it.isNotRetrofitCancelled())
                        log.error("retrieveLegacyPlaylist({}) failed", broadcastId, it)
                }
                .map { Playlist.loadLegacyPlaylist(it) }
    }
}