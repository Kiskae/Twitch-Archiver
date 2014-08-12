package net.serverpeon.twitcharchiver.downloader;

public interface BiConsumer<F, T> {

    public void consume(F first, T second);
}
