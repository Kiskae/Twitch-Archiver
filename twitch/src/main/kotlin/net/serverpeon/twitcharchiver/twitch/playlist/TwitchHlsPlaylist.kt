package net.serverpeon.twitcharchiver.twitch.playlist

import com.google.common.base.Preconditions.checkState
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.io.Resources
import net.serverpeon.twitcharchiver.hls.*
import net.serverpeon.twitcharchiver.hls.OfficialTags.toDuration
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

internal object TwitchHlsPlaylist {
    private const val END_OFFSET_PARAM = "end_offset"
    // Experimentally determined to be the maximum Twitch allows
    // Any longer and it refuses to respond correctly.
    private val TWITCH_MAXIMUM_SEGMENT_DURATION = Duration.ofSeconds(19)
    private val log = LoggerFactory.getLogger(TwitchHlsPlaylist::class.java)
    private val REDUCE_DISABLED = System.getProperty("twitch.dontreduce", "false").toBoolean()

    private val HLS_ENCODING_PROPS = EncodingDescription(
            ImmutableList.of("-bsf:a", "aac_adtstoasc"),
            EncodingDescription.IOType.INPUT_CONCAT
    )

    init {
        if (REDUCE_DISABLED) {
            log.info("Playlist reduction disabled")
        }
    }

    private val EXT_X_TWITCH_TOTAL_SECS: HlsTag<Duration> = HlsTag("EXT-X-TWITCH-TOTAL-SECS", appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST) {
        it.toDuration()
    }
    val TWITCH_HLS_TAG_REPOSITORY: TagRepository = TagRepository.newRepository().apply {
        register(EXT_X_TWITCH_TOTAL_SECS)

        fun String.toResolution(): AttributeListParser.Resolution {
            return if (this[0] == '"') {
                this.substring(1, this.length - 1)
            } else {
                this
            }.let { resolution ->
                val pivot = resolution.indexOf('x')

                checkState(pivot != -1 && (pivot < resolution.length - 1), "Malformed attribute list, invalid resolution format")

                try {
                    val width = resolution.substring(0, pivot).toInt()
                    val height = resolution.substring(pivot + 1).toInt()
                    AttributeListParser.Resolution(width, height)
                } catch (ex: NumberFormatException) {
                    throw IllegalStateException("Malformed attribute list, invalid resolution value", ex)
                }
            }
        }

        // Override for Twitch
        register(HlsTag(
                "EXT-X-STREAM-INF",
                appliesTo = HlsTag.AppliesTo.NEXT_SEGMENT,
                unique = true
        ) { rawData ->
            val parser = AttributeListParser(rawData)

            var bandwidth: Long? = null
            var programId: Long? = null
            var codecs: String? = null
            var resolution: AttributeListParser.Resolution? = null
            var audio: String? = null
            var video: String? = null

            while (parser.hasMoreAttributes()) {
                when (parser.readAttributeName()) {
                    "BANDWIDTH" -> bandwidth = parser.readDecimalInt()
                    "PROGRAM-ID" -> programId = parser.readDecimalInt()
                    "CODECS" -> codecs = parser.readQuotedString()
                // Twitch returns a quoted version of the resolution for some reason
                    "RESOLUTION" -> resolution = parser.readEnumeratedString().toResolution()
                    "AUDIO" -> audio = parser.readQuotedString()
                    "VIDEO" -> video = parser.readQuotedString()
                }
            }

            OfficialTags.StreamInformation(
                    bandwidth!!,
                    programId,
                    codecs?.let { ImmutableList.copyOf(it.split(',')) } ?: ImmutableList.of(),
                    resolution,
                    audio,
                    video
            )
        }, override = true)
    }

    fun load(broadcastId: String, stream: HlsPlaylist.Variant): Playlist {
        val actualPlaylist = HlsParser.parseSimplePlaylist(
                stream.uri,
                Resources.asCharSource(stream.uri.toURL(), Charsets.UTF_8),
                TWITCH_HLS_TAG_REPOSITORY
        )

        val videos = reduceVideos(actualPlaylist, stream.uri)
        return Playlist(
                broadcastId,
                ImmutableList.copyOf(videos),
                actualPlaylist[EXT_X_TWITCH_TOTAL_SECS] ?: fallbackCalculateDuration(videos),
                HLS_ENCODING_PROPS
        )
    }

    private fun fallbackCalculateDuration(videos: List<Playlist.Video>): Duration {
        if (videos.isEmpty()) {
            return Duration.ZERO
        } else {
            return videos.map { it.length }.reduce { length1, length2 ->
                length1 + length2
            }
        }
    }

    private fun reduceVideos(videos: List<HlsPlaylist.Segment>, source: URI): List<Playlist.Video> {
        val ret: MutableList<Playlist.Video> = Lists.newArrayList()
        val it = videos.iterator()

        var lastVideo = it.next()

        var startVideoUrl = HttpUrl.get(lastVideo.uri)
        var length = lastVideo.info.duration

        if (REDUCE_DISABLED || startVideoUrl.queryParameter(END_OFFSET_PARAM) == null) {
            log.debug("Irreducible video: {}", source)
            return videos.map {
                val uri = HttpUrl.get(it.uri)
                Playlist.Video(
                        uri,
                        it.info.duration,
                        muted = uri.encodedPath().endsWith("-muted.ts")
                )
            }
        }

        while (it.hasNext()) {
            val nextVideo = it.next()
            val nextUrl = HttpUrl.get(nextVideo.uri)
            if (startVideoUrl.encodedPath() != nextUrl.encodedPath()
                    || (length + nextVideo.info.duration) >= TWITCH_MAXIMUM_SEGMENT_DURATION) {
                //We've gone past the previous video segment, finalize it
                ret.add(constructVideo(startVideoUrl, HttpUrl.get(lastVideo.uri), length))

                startVideoUrl = nextUrl
                length = Duration.ZERO
            }

            length += nextVideo.info.duration
            lastVideo = nextVideo
        }

        ret.add(constructVideo(startVideoUrl, HttpUrl.get(lastVideo.uri), length))
        log.debug("reduce on {}: {} -> {}", source, videos.size, ret.size)
        return ret
    }

    private fun constructVideo(start: HttpUrl, end: HttpUrl, length: Duration): Playlist.Video {
        return start.newBuilder()
                // Replace the end offset of the starting url with the ending url
                .setQueryParameter(END_OFFSET_PARAM, end.queryParameter(END_OFFSET_PARAM))
                .build()
                .let { url ->
                    Playlist.Video(
                            url,
                            length,
                            muted = url.encodedPath().endsWith("-muted.ts")
                    )
                }
    }
}
