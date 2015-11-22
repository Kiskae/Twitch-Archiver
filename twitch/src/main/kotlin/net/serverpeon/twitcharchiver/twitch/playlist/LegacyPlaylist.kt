package net.serverpeon.twitcharchiver.twitch.playlist

import com.google.common.collect.ImmutableList
import com.squareup.okhttp.HttpUrl
import net.serverpeon.twitcharchiver.twitch.api.InternalApi
import java.time.Duration
import java.util.*

internal object LegacyPlaylist {
    fun load(source: InternalApi.LegacyVideoSource): Optional<Playlist> {
        val videos = source.chunks.live.map {
            Playlist.Video(
                    HttpUrl.parse(it.url),
                    Duration.ofSeconds(it.length),
                    muted = "fail".equals(it.keep)
            )
        }

        if (videos.isNotEmpty()) {
            return Optional.of(Playlist(
                    ImmutableList.copyOf(videos),
                    Duration.ofSeconds(source.duration)
            ))
        } else {
            return Optional.empty()
        }
    }
}