package net.serverpeon.twitcharchiver.twitch;

public class UnrecognizedVodFormatException extends RuntimeException {
    public UnrecognizedVodFormatException(String url) {
        super(url);
    }
}
