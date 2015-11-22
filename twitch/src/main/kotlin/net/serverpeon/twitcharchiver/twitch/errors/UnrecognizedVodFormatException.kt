package net.serverpeon.twitcharchiver.twitch.errors

/**
 * Thrown if the API requests an unsupported VoD format.
 * Current v (HLS) and (a) Legacy are supported.
 */
class UnrecognizedVodFormatException(broadcastId: String) : TwitchApiException(
        "Unknown VoD format ($broadcastId), please tell @KiskaeEU",
        null
) {
}