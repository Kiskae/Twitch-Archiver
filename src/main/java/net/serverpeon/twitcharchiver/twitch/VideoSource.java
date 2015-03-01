package net.serverpeon.twitcharchiver.twitch;

import net.serverpeon.twitcharchiver.downloader.ForkJoinDownloadTask;
import net.serverpeon.twitcharchiver.downloader.ProgressTracker;

import java.io.File;

public interface VideoSource {
    ForkJoinDownloadTask createDownloadTask(final File targetFolder, ProgressTracker progressTracker);

    int getNumberOfParts();

    int getNumberOfMutedParts();
}
