package net.serverpeon.twitcharchiver.hls;

public class MalformedPlaylistException extends RuntimeException {
    public MalformedPlaylistException(String message) {
        super(message);
    }
}
