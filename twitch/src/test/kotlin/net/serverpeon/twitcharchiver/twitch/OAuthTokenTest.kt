package net.serverpeon.twitcharchiver.twitch

import org.junit.Test
import kotlin.test.assertFalse

class OAuthTokenTest {

    @Test
    fun testCensoredToString() {
        val testString = "HelloWorldHiTwitch"
        val oauth = OAuthToken(testString)
        assertFalse {
            oauth.toString().contains(testString)
        }
    }
}