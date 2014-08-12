package net.serverpeon.twitcharchiver.twitch;

public class InvalidOAuthTokenException extends Exception {
    public InvalidOAuthTokenException(final String token) {
        super(String.format("'%s' is not a valid Twitch OAuth token.", token));
    }
}
