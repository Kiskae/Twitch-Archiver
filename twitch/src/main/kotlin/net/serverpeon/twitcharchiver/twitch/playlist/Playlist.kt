package net.serverpeon.twitcharchiver.twitch.playlist

import com.squareup.okhttp.HttpUrl
import net.serverpeon.twitcharchiver.hls.HlsPlaylist
import net.serverpeon.twitcharchiver.twitch.api.InternalApi
import java.time.Duration

/**
 * Playlist of videos for a broadcast on Twitch
 */
data class Playlist internal constructor(val videos: List<Playlist.Video>, val length: Duration) {
    companion object {
        fun loadHlsPlaylist(stream: HlsPlaylist.Variant): Playlist =
                TwitchHlsPlaylist.load(stream)

        fun loadLegacyPlaylist(source: InternalApi.LegacyVideoSource): Playlist =
                LegacyPlaylist.load(source)
                        .orElseThrow {
                            AssertionError()
                        }
    }

    fun parts(): Int {
        return videos.size
    }

    fun mutedParts(): Int {
        return videos.count { it.muted }
    }

    fun length(): Duration {
        return length
    }

    data class Video(val resource: HttpUrl, val length: Duration, val muted: Boolean)
}