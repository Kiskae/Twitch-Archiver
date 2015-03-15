package net.serverpeon.twitcharchiver.twitch;

import net.serverpeon.twitcharchiver.ui.DialogEnabled;

public class SubscriberOnlyException extends RuntimeException implements DialogEnabled {
    @Override
    public String getTitle() {
        return "I cannae see them, captain";
    }

    @Override
    public String getBody() {
        return "These videos are limited to subscribers, this should only show" +
                " up if you're messing with code.";
    }
}
