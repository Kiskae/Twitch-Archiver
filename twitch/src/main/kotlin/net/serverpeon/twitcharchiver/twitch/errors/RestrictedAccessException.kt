package net.serverpeon.twitcharchiver.twitch.errors

import java.io.IOException

/**
 * Thrown if a playlist is accessed which cannot be accessed with the current user's authorization
 *
 * Tends to happen if the user does not have a channel subscription or editor access to a channel.
 * (And the VoDs are restricted to channel subscribers)
 */
class RestrictedAccessException(vodId: Long, ex: IOException) : TwitchApiException(
        "Requested VoD $vodId cannot be accessed due access exception (channel subscription)",
        ex
) {
}