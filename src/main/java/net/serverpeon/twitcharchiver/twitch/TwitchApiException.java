package net.serverpeon.twitcharchiver.twitch;

import javax.ws.rs.core.Response;

public class TwitchApiException extends RuntimeException {
    public TwitchApiException(final Response webResponse) {
        super("Invalid Response by Twitch API: " + webResponse);
    }
}
