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

    @Test
    fun testTwitchCompatibility() {
        val extTag = TwitchHlsPlaylist.TWITCH_HLS_TAG_REPOSITORY["EXT-X-STREAM-INF"]!!
        // Bandwidth and resolution incompatible -> 14/07
        assertNotNull(extTag.processor("PROGRAM-ID=1,BANDWIDTH=2297945.0,CODECS=\"avc1.4D4020,mp4a.40.2\"," +
                "RESOLUTION=\"1280x720\",VIDEO=\"chunked\""))
        // Bandwidth compatible, resolution incompatible -> 15/07
        assertNotNull(extTag.processor("PROGRAM-ID=1,BANDWIDTH=2804395,CODECS=\"avc1.4D4020,mp4a.40.2\"," +
                "RESOLUTION=\"1280x720\",VIDEO=\"chunked\""))
        // Bandwidth and resolution compatible -> ??
        assertNotNull(extTag.processor("PROGRAM-ID=1,BANDWIDTH=2297945,CODECS=\"avc1.4D4020,mp4a.40.2\"," +
                "RESOLUTION=1280x720,VIDEO=\"chunked\""))
    }
}