package net.serverpeon.twitcharchiver.downloader;

import java.io.File;

public interface VideoSource {
    Runnable createDownloadTask(final File targetFolder, ProgressTracker progressTracker);

    int getNumberOfParts();

    int getNumberOfMutedParts();
}
