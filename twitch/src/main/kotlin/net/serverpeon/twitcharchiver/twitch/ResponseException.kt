package net.serverpeon.twitcharchiver.twitch

import retrofit.Response

class ResponseException(val resp: Response<*>) : RuntimeException(resp.message()) {
}