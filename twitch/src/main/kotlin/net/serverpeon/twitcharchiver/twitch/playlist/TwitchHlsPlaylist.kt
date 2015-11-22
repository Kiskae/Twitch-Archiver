package net.serverpeon.twitcharchiver.twitch.playlist

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.io.Resources
import com.squareup.okhttp.HttpUrl
import net.serverpeon.twitcharchiver.hls.*
import java.time.Duration

internal object TwitchHlsPlaylist {
    private const val END_OFFSET_PARAM = "end_offset"
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

        val videos = reduceVideos(actualPlaylist)
        return Playlist(
                ImmutableList.copyOf(videos),
                actualPlaylist[EXT_X_TWITCH_TOTAL_SECS]!!
        )
    }

    private fun reduceVideos(videos: List<HlsPlaylist.Segment>): List<Playlist.Video> {
        val ret: MutableList<Playlist.Video> = Lists.newArrayList()
        val it = videos.iterator()

        var lastVideo = it.next()

        var startVideoUrl = HttpUrl.get(lastVideo.uri)
        var length = lastVideo.info.duration

        while (it.hasNext()) {
            val nextVideo = it.next()
            val nextUrl = HttpUrl.get(nextVideo.uri)
            if (!startVideoUrl.encodedPath().equals(nextUrl.encodedPath())) {
                //We've gone past the previous video segment, finalize it
                ret.add(constructVideo(startVideoUrl, HttpUrl.get(lastVideo.uri), length))

                startVideoUrl = nextUrl
                length = Duration.ZERO
            }

            length += nextVideo.info.duration
            lastVideo = nextVideo
        }

        ret.add(constructVideo(startVideoUrl, HttpUrl.get(lastVideo.uri), length))
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