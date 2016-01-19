package net.serverpeon.twitcharchiver.twitch.playlist

import net.serverpeon.twitcharchiver.hls.HlsPlaylist
import net.serverpeon.twitcharchiver.twitch.api.InternalApi
import okhttp3.HttpUrl
import java.time.Duration
import kotlin.collections.count

/**
 * Playlist of videos for a broadcast on Twitch
 */
data class Playlist internal constructor(val videos: List<Playlist.Video>, val length: Duration, val encoding: EncodingDescription) {
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