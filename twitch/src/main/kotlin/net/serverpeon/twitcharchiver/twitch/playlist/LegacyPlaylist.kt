package net.serverpeon.twitcharchiver.twitch.playlist

import com.google.common.collect.ImmutableList
import net.serverpeon.twitcharchiver.twitch.api.InternalApi
import okhttp3.HttpUrl
import java.time.Duration
import java.util.*
import kotlin.collections.isNotEmpty
import kotlin.collections.map

internal object LegacyPlaylist {
    private val LEGACY_ENCODING_PROPS = EncodingDescription(ImmutableList.of(), EncodingDescription.IOType.FILE_CONCAT)

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
                    Duration.ofSeconds(source.duration),
                    LEGACY_ENCODING_PROPS
            ))
        } else {
            return Optional.empty()
        }
    }
}