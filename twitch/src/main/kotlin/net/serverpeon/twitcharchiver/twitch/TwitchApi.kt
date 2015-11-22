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
import retrofit.*
import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Unified access to all the API calls required to download broadcasts from twitch.
 */
class TwitchApi(token: OAuthToken) {
    companion object {
        private val TWITCH_API_URL = HttpUrl.parse("https://api.twitch.tv/")
        private const val BROADCASTS_PER_REQUEST: Int = 100
        private const val TOP_QUALITY_STREAM: String = "chunked"
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
                println(chain.request().urlString())
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

    /**
     * Retrieves the user associated with the provided [OAuthToken]
     *
     * If the OAuth token is invalid then the optional will be empty
     */
    @Throws(TwitchApiException::class, IOException::class)
    fun retrieveUser(): CompletableFuture<Optional<String>> {
        return krakenApi.authStatus().enqueue().thenApplyAsync { user ->
            if (user.token?.valid ?: false) {
                Optional.of(user.token!!.userName!!)
            } else {
                Optional.empty()
            }
        }.exceptionally { Optional.empty() }
    }

    /**
     * Retrieves a list of videos for the given channel, if no limit is given then it will
     * provide all the videos available on the channel.
     */
    @Throws(IOException::class)
    fun videoList(channelName: String, limit: Int = -1): CompletableFuture<Iterator<KrakenApi.VideoListResponse.Video>> {
        require(limit != 0) { "Limit must be higher than 0" }

        if (limit > 0 && limit <= BROADCASTS_PER_REQUEST) {
            return krakenApi.videoList(channelName).enqueue().thenApply { it.videos.iterator() }
        } else {
            return krakenApi.videoList(channelName).enqueue().thenApply { ret ->
                val totalVideos = if (limit < 0) ret.totalVideos else Math.min(limit, ret.totalVideos)

                object : Iterator<KrakenApi.VideoListResponse.Video> {
                    var internalIterator = ret.videos.iterator()
                    var nextOffset = BROADCASTS_PER_REQUEST

                    override fun hasNext(): Boolean {
                        return internalIterator.hasNext() || nextOffset < totalVideos
                    }

                    override fun next(): KrakenApi.VideoListResponse.Video {
                        if (!internalIterator.hasNext() && nextOffset < totalVideos) {
                            val nextResponse = krakenApi.videoList(
                                    channelName,
                                    limit = Math.min(BROADCASTS_PER_REQUEST, totalVideos - nextOffset),
                                    offset = nextOffset
                            ).execute() //Blocking, but what can you do?
                            // Maybe use Rx?

                            internalIterator = nextResponse.body().videos.iterator()
                            nextOffset += BROADCASTS_PER_REQUEST
                        }

                        if (internalIterator.hasNext()) {
                            return internalIterator.next()
                        } else {
                            throw NoSuchElementException()
                        }
                    }

                }
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
    fun loadPlaylist(broadcastId: String): CompletableFuture<Playlist> {
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

    private fun retrieveHlsPlaylist(broadcastId: Long): CompletableFuture<Playlist> {
        return internalApi.requestVodAccess(broadcastId)
                .enqueue()
                .thenApplyAsync { auth ->
                    val url = UsherApi.buildResourceUrl(broadcastId, auth)
                    try {
                        val variants = HlsParser.parseVariantPlaylist(
                                url.uri(),
                                Resources.asCharSource(url.url(), Charsets.UTF_8),
                                TagRepository.DEFAULT
                        )
                        val bestQualityStream = variants.sortedByDescending { it.info.bandwidth }.first()

                        check(bestQualityStream.info.video?.equals(TOP_QUALITY_STREAM) ?: false) {
                            "Best quality stream is not '$TOP_QUALITY_STREAM', something is wrong"
                        }

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

    private fun retrieveLegacyPlaylist(broadcastId: String): CompletableFuture<Playlist> {
        return internalApi.videoData(broadcastId)
                .enqueue()
                .thenApplyAsync { Playlist.loadLegacyPlaylist(it) }
    }

    private fun <T> Call<T>.enqueue(): CompletableFuture<T> {
        val future: CompletableFuture<T> = CompletableFuture()

        this.enqueue(object : Callback<T> {
            override fun onFailure(t: Throwable?) {
                future.completeExceptionally(t)
            }

            override fun onResponse(response: Response<T>, retrofit: Retrofit?) {
                if (response.isSuccess) {
                    future.complete(response.body())
                } else {
                    future.completeExceptionally(ResponseException(response))
                }
            }

        })

        return future
    }
}