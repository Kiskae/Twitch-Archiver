package net.serverpeon.twitcharchiver.hls

import com.google.common.io.Resources
import org.junit.Ignore
import org.junit.Test
import java.net.URL
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HlsParserTest {

    @Test
    fun testSimplePlaylist() {
        val url: URL = Resources.getResource("playlists/simple.m3u8")
        val playlist = HlsParser.parseSimplePlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))

        assertEquals(Duration.ofSeconds(5220), playlist.maximum_segment_duration)
        for (segment in playlist) {
            assertEquals(Duration.ofSeconds(5220), segment.info.duration)
        }
        assertEquals(1, playlist.size)
    }

    @Test
    fun testSlidingWindow() {
        val url: URL = Resources.getResource("playlists/sliding_window.m3u8")
        val playlist = HlsParser.parseSimplePlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))

        assertEquals(Duration.ofSeconds(8), playlist.maximum_segment_duration)
        for (segment in playlist) {
            assertEquals(Duration.ofSeconds(8), segment.info.duration)
        }
        assertEquals(3, playlist.size)
    }

    @Test
    fun testEncryptedSegments() {
        val url: URL = Resources.getResource("playlists/encrypted_playlist.m3u8")
        val playlist = HlsParser.parseSimplePlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))
    }

    @Test
    fun testVariantPlaylist() {
        val url: URL = Resources.getResource("playlists/variant_playlist.m3u8")
        val playlist = HlsParser.parseVariantPlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))

        val seenBandwidths: MutableSet<Long> = HashSet()
        for (variant in playlist) {
            assertTrue { seenBandwidths.add(variant.info.bandwidth) }
            assertEquals(1, variant.info.programId)
        }
    }

    @Test
    @Ignore
    fun testPlaylistWithIFrames() {
        val url: URL = Resources.getResource("playlists/variant_playlist_iframes.m3u8")
        val playlist = HlsParser.parseVariantPlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))
    }

    @Test
    fun testVariantPlaylistWithAudio() {
        val url: URL = Resources.getResource("playlists/variant_alternative_audio.m3u8")
        val playlist = HlsParser.parseVariantPlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))
    }

    @Test
    fun testVariantPlaylistWithVideo() {
        val url: URL = Resources.getResource("playlists/variant_alternative_video.m3u8")
        val playlist = HlsParser.parseVariantPlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))
    }

    @Test
    fun testTwitchPlaylist() {
        val url: URL = Resources.getResource("playlists/twitch.m3u8")
        val playlist = HlsParser.parseSimplePlaylist(url.toURI(), Resources.asCharSource(url, Charsets.UTF_8))
    }
}