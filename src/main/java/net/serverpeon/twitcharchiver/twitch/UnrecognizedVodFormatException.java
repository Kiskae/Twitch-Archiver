package net.serverpeon.twitcharchiver.twitch;

public class UnrecognizedVodFormatException extends RuntimeException {
    public UnrecognizedVodFormatException(String url) {
        super(url);
    }

    public UnrecognizedVodFormatException(Exception ex) {
        super(ex);
    }
}
