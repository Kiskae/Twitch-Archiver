package net.serverpeon.twitcharchiver.twitch;

import net.serverpeon.twitcharchiver.ui.DialogEnabled;

public class UnrecognizedVodFormatException extends RuntimeException implements DialogEnabled {
    public UnrecognizedVodFormatException(String url) {
        super(url);
    }

    public UnrecognizedVodFormatException(Exception ex) {
        super(ex);
    }

    @Override
    public String getTitle() {
        return "Error 37: Twitch";
    }

    @Override
    public String getBody() {
        return "Something failed while retrieving information from twitch, " +
                "please contact @KiskaeEU on twitter.";
    }
}
