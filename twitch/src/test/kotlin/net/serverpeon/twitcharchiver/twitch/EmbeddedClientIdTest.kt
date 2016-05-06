package net.serverpeon.twitcharchiver.twitch

import org.junit.Test

class EmbeddedClientIdTest {

    @Test
    fun testClientIdLoaded() {
        check(TwitchApi.TWITCH_CLIENT_ID.isNotEmpty())
    }
}