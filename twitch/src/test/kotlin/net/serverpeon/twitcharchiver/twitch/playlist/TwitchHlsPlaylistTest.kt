package net.serverpeon.twitcharchiver.twitch.playlist

import com.google.common.io.Resources
import net.serverpeon.twitcharchiver.hls.HlsParser
import org.junit.Test
import java.net.URL
import kotlin.test.assertNotNull

class TwitchHlsPlaylistTest {
    @Test
    fun testTwitchPlaylist() {
        val url: URL = Resources.getResource("playlists/twitch.m3u8")
        val playlist = HlsParser.parseSimplePlaylist(
                url.toURI(),
                Resources.asCharSource(url, Charsets.UTF_8),
                TwitchHlsPlaylist.TWITCH_HLS_TAG_REPOSITORY
        )
        assertNotNull(playlist)
    }

    @Test
    fun testTwitchVariants() {
        val url: URL = Resources.getResource("playlists/twitch_variant.m3u8")
        val playlist = HlsParser.parseSimplePlaylist(
                url.toURI(),
                Resources.asCharSource(url, Charsets.UTF_8),
                TwitchHlsPlaylist.TWITCH_HLS_TAG_REPOSITORY
        )
        assertNotNull(playlist)
    }
}