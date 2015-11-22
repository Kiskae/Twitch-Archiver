package net.serverpeon.twitcharchiver.twitch.errors

/**
 * Base exception for any exceptions caused by the Twitch API
 */
open class TwitchApiException(msg: String, ex: Throwable?) : RuntimeException(msg, ex) {
}