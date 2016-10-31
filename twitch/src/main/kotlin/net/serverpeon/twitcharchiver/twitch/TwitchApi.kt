package net.serverpeon.twitcharchiver.twitch

import com.google.common.collect.ImmutableList
import com.google.common.io.Resources
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TwitchApi(val gson: Gson, val client: OkHttpClient) {
    companion object {
        private fun loadClientId(): String {
            val props = Properties()
            LegacyTwitchApi::class.java.getResourceAsStream("/api.properties").use {
                props.load(it)
            }
            return props.getProperty("clientId")
        }

        val TWITCH_CLIENT_ID: String by lazy { loadClientId() }

        private val TWITCH_API_URL = HttpUrl.parse("https://api.twitch.tv/")
        private val log = LoggerFactory.getLogger(TwitchApi::class.java)
        private const val BROADCASTS_PER_REQUEST: Int = 100
        private const val TOP_QUALITY_STREAM: String = "chunked"

        fun createGson(): Gson {
            return GsonBuilder().apply {
                registerTypeAdapter(Duration::class.java, DurationAdapter)
                registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter)
            }.create()
        }

        fun createInterceptor(token: String): Interceptor = OAuthInterceptor.static(token, TWITCH_CLIENT_ID)
    }

    private inline fun <reified T : Any> Retrofit.lazyCreate(): Lazy<T> {
        return lazy { this.create(T::class.java) }
    }

    private val retrofit: Retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createWithScheduler(Schedulers.io()))
            .baseUrl(TWITCH_API_URL)
            .client(client)
            .build()

    private val krakenApi: KrakenApi by retrofit.lazyCreate()
    private val internalApi: InternalApi by retrofit.lazyCreate()

    private val limiter = RateLimitingRx(Schedulers.computation(), 500, TimeUnit.MILLISECONDS)

    fun retrieveUser(): Maybe<String> {
        log.trace("retrieveUser()")
        return krakenApi.authStatus()
                .filter { user ->
                    user.identified?.apply {
                        check(this) { "Client-ID identification failure" }
                    }

                    user.token?.valid ?: false
                }.map { it.token!!.userName!! }
    }

    fun videoList(channelName: String, limit: Int = -1): Flowable<KrakenApi.VideoListResponse.Video> {
        require(limit != 0) { "Limit must be higher than 0" }

        return if (limit > 0 && limit <= BROADCASTS_PER_REQUEST) {
            krakenApi.videoList(channelName, limit = limit)
                    .flatMapPublisher {
                        Flowable.fromIterable(it.videos)
                    }
        } else {
            data class VideoListState(val offset: Int, val finished: AtomicBoolean)

            val limiter = RateLimitingRx(Schedulers.computation(), 5, TimeUnit.SECONDS)

            val generator: (VideoListState, Emitter<Publisher<KrakenApi.VideoListResponse.Video>>) -> VideoListState =
                    { state, emitter ->
                        val toRequest = Math.min(limit - state.offset, BROADCASTS_PER_REQUEST)

                        if (toRequest == 0 || state.finished.get()) {
                            emitter.onComplete()
                            state
                        } else {
                            log.debug("videoList({},{}) [toRequest = {}, total = {}]", channelName, limit, toRequest, limit)

                            emitter.onNext(limiter.rx().flatMap {
                                krakenApi.videoList(
                                        channelName = channelName,
                                        limit = toRequest,
                                        offset = state.offset
                                )
                            }.doOnSuccess { response ->
                                if (response.totalVideos <= state.offset + toRequest) {
                                    state.finished.set(true)
                                }
                            }.flatMapPublisher {
                                Flowable.fromIterable(it.videos)
                            })

                            state.copy(offset = state.offset + toRequest)
                        }
                    }
            val broadcasts = Flowable.concat(Flowable.generate({
                VideoListState(0, AtomicBoolean(false))
            }, generator), 1)

            if (limit < 0) {
                broadcasts
            } else {
                broadcasts.take(limit.toLong())
            }
        }
    }

    @Throws(TwitchApiException::class)
    fun loadPlaylist(broadcastId: String): Single<Playlist> {
        return if (broadcastId.startsWith('a')) {
            //Old style video storage
            retrieveLegacyPlaylist(broadcastId)
        } else if (broadcastId.startsWith('v')) {
            //HLS style video storage
            retrieveHlsPlaylist(broadcastId.substring(1).toLong())
        } else {
            throw UnrecognizedVodFormatException(broadcastId)
        }.doOnSubscribe {
            log.trace("loadPlaylist({})", broadcastId)
        }.compose { req ->
            limiter.rx().flatMap {
                req.retryWhen { errors ->
                    errors.takeUntil { th: Throwable ->
                        th is RestrictedAccessException
                    }.zipWith<Int, Int>(Flowable.range(1, 3), BiFunction { n, i ->
                        i
                    }).flatMap { retryCount ->
                        Flowable.timer(Math.pow(2.0, retryCount.toDouble()).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
        }
    }

    private fun retrieveHlsPlaylist(broadcastId: Long): Single<Playlist> {
        return internalApi.requestVodAccess(broadcastId)
                .doOnSubscribe {
                    log.trace("retrieveHlsPlaylist({})", broadcastId)
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
                        Playlist.loadHlsPlaylist("v$broadcastId", bestQualityStream)
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
        return internalApi.videoData(broadcastId)
                .doOnSubscribe {
                    log.trace("retrieveLegacyPlaylist({})", broadcastId)
                }
                .map {
                    Playlist.loadLegacyPlaylist(broadcastId, it)
                }
    }
}