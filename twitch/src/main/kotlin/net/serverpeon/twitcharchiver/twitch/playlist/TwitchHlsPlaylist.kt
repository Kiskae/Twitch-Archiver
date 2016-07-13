package net.serverpeon.twitcharchiver.twitch.playlist

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.io.Resources
import net.serverpeon.twitcharchiver.hls.HlsParser
import net.serverpeon.twitcharchiver.hls.HlsPlaylist
import net.serverpeon.twitcharchiver.hls.HlsTag
import net.serverpeon.twitcharchiver.hls.OfficialTags.toDuration
import net.serverpeon.twitcharchiver.hls.TagRepository
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

internal object TwitchHlsPlaylist {
    private const val END_OFFSET_PARAM = "end_offset"
    // Experimentally determined to be the maximum Twitch allows
    // Any longer and it refuses to respond correctly.
    private val TWITCH_MAXIMUM_SEGMENT_DURATION = Duration.ofSeconds(20)
    private val log = LoggerFactory.getLogger(TwitchHlsPlaylist::class.java)

    private val HLS_ENCODING_PROPS = EncodingDescription(
            ImmutableList.of("-bsf:a", "aac_adtstoasc"),
            EncodingDescription.IOType.INPUT_CONCAT
    )

    private val EXT_X_TWITCH_TOTAL_SECS: HlsTag<Duration> = HlsTag("EXT-X-TWITCH-TOTAL-SECS", appliesTo = HlsTag.AppliesTo.ENTIRE_PLAYLIST) {
        it.toDuration()
    }
    private val TWITCH_HLS_TAG_REPOSITORY: TagRepository = TagRepository.newRepository().apply {
        register(EXT_X_TWITCH_TOTAL_SECS)
    }

    fun load(stream: HlsPlaylist.Variant): Playlist {
        val actualPlaylist = HlsParser.parseSimplePlaylist(
                stream.uri,
                Resources.asCharSource(stream.uri.toURL(), Charsets.UTF_8),
                TWITCH_HLS_TAG_REPOSITORY
        )

        val videos = reduceVideos(actualPlaylist, stream.uri)
        return Playlist(
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

        while (it.hasNext()) {
            val nextVideo = it.next()
            val nextUrl = HttpUrl.get(nextVideo.uri)
            if (!startVideoUrl.encodedPath().equals(nextUrl.encodedPath())
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