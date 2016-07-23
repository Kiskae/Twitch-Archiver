package net.serverpeon.twitcharchiver.twitch

import com.google.common.collect.ImmutableList
import com.google.common.io.Resources
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.serverpeon.twitcharchiver.hls.HlsParser
import net.serverpeon.twitcharchiver.twitch.api.InternalApi
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.api.UsherApi
import net.serverpeon.twitcharchiver.twitch.errors.RestrictedAccessException
import net.serverpeon.twitcharchiver.twitch.errors.TwitchApiException
import net.serverpeon.twitcharchiver.twitch.errors.UnrecognizedVodFormatException
import net.serverpeon.twitcharchiver.twitch.json.DurationAdapter
import net.serverpeon.twitcharchiver.twitch.json.ZonedDateTimeAdapter
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import net.serverpeon.twitcharchiver.twitch.playlist.TwitchHlsPlaylist
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.Single
import rx.observables.AsyncOnSubscribe
import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified access to all the API calls required to download broadcasts from twitch.
 */
class TwitchApi(token: OAuthToken) {
    companion object {
        private fun loadClientId(): String {
            val props = Properties()
            TwitchApi::class.java.getResourceAsStream("/api.properties").use {
                props.load(it)
            }
            return props.getProperty("clientId")
        }

        val TWITCH_CLIENT_ID: String by lazy { loadClientId() }
        private val TWITCH_API_URL = HttpUrl.parse("https://api.twitch.tv/")
        private const val BROADCASTS_PER_REQUEST: Int = 100
        private const val TOP_QUALITY_STREAM: String = "chunked"
        private val log = LoggerFactory.getLogger(TwitchApi::class.java)
    }

    private val gson: Gson = GsonBuilder().apply {
        registerTypeAdapter(Duration::class.java, DurationAdapter)
        registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter)
    }.create()

    private val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(OAuthInterceptor(token, TWITCH_CLIENT_ID)).apply {
        System.getProperty("http.debug")?.let { level ->
            val interceptLogger = LoggerFactory.getLogger(OkHttpClient::class.java)
            addInterceptor(
                    HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
                        interceptLogger.trace(it)
                    }).setLevel(HttpLoggingInterceptor.Level.valueOf(level.toUpperCase()))
            )
        }
    }.build()

    private val retrofit: Retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .baseUrl(TWITCH_API_URL)
            .build()

    private val krakenApi: KrakenApi by lazy { retrofit.create(KrakenApi::class.java) }
    private val internalApi: InternalApi by lazy { retrofit.create(InternalApi::class.java) }

    private fun Throwable.isNotRetrofitCancelled(): Boolean {
        return this !is SubscriberCallback.CancelledException
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
                    user.identified?.apply {
                        check(this) { "Client-ID identification failure" }
                    }

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

        if (limit > 0 && limit <= BROADCASTS_PER_REQUEST) {
            return krakenApi.videoList(channelName, limit = limit)
                    .toRx()
                    .toObservable()
                    .flatMapIterable { it.videos }
        } else {
            data class VideoListState(val offset: Long, val finished: AtomicBoolean)

            return Observable.create(AsyncOnSubscribe.createStateful<VideoListState, KrakenApi.VideoListResponse.Video>({
                VideoListState(0, AtomicBoolean(false))
            }) { state, requested, observable ->
                // Have we exhausted all videos?
                if (!state.finished.get()) {
                    // Request at most BROADCASTS_PER_REQUEST at the time
                    val toRequest = Math.min(requested, BROADCASTS_PER_REQUEST.toLong())
                    log.debug("videoList({},{}) [toRequest = {}, total = {}]", channelName, limit, toRequest, requested)

                    // Emit the next set of videos as sent by the API
                    observable.onNext(krakenApi.videoList(
                            channelName,
                            limit = toRequest.toInt(),
                            offset = state.offset.toInt()
                    ).toRx().doOnSuccess { response ->
                        // Check if there are any videos left
                        if (response.totalVideos <= state.offset + toRequest) {
                            // The Rx contract states you cannot do recursive calls, so we
                            // defer onCompleted until the next update
                            state.finished.set(true)
                        }
                    }.toObservable().flatMapIterable {
                        it.videos
                    })

                    state.copy(offset = state.offset + toRequest)
                } else {
                    observable.onCompleted()
                    state
                }
            }).compose {
                // If limit is set, impose the limit, this will automatically
                // affect backpressure
                if (limit < 0) {
                    it
                } else {
                    it.limit(limit)
                }
                // Set a buffer equal to the maximum, ensures it always tries to request as much as it can.
            }.onBackpressureBuffer(BROADCASTS_PER_REQUEST.toLong())
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
                    val url = UsherApi.buildResourceUrl(broadcastId, auth, gson)
                    log.debug("Loading variant playlist for {}: {}", broadcastId, url)
                    try {
                        val variants = HlsParser.parseVariantPlaylist(
                                url.uri(),
                                Resources.asCharSource(url.url(), Charsets.UTF_8),
                                TwitchHlsPlaylist.TWITCH_HLS_TAG_REPOSITORY
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