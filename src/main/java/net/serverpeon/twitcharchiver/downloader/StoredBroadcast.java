package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Throwables;
import net.serverpeon.twitcharchiver.twitch.BroadcastInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public class StoredBroadcast {
    private final static int PROGRESS_MAX = 100;
    private final static Logger logger = LogManager.getLogger(StoredBroadcast.class);
    private final static PeriodFormatter timeFormatter = new PeriodFormatterBuilder()
            .appendHours()
            .appendSeparator(":")
            .printZeroAlways()
            .minimumPrintedDigits(2)
            .appendMinutes()
            .appendSeparator(":")
            .appendSeconds()
            .toFormatter();
    private final static DateTimeFormatter dateFormatter = DateTimeFormat
            .forPattern(DateTimeFormat.patternForStyle("MM", null));

    private final JProgressBar downloadProgress = new JProgressBar(0, PROGRESS_MAX);

    private final BroadcastInformation bi;
    private final File storageFolder;
    private final ProgressTracker tracker;

    private final int numberOfParts;

    private boolean selected;

    StoredBroadcast(BroadcastInformation bi, File rootStorageDirectory) {
        this.bi = bi;
        this.storageFolder = new File(rootStorageDirectory, String.format(
                "%s-%s",
                bi.broadcastId,
                bi.title.replaceAll("[^a-zA-Z0-9\\-]", "_") //Strip characters to make safe for filesystem
        ));
        this.selected = this.storageFolder.exists();
        this.numberOfParts = this.bi.getSource().getNumberOfParts();
        this.tracker = new ProgressTracker(storageFolder, this.numberOfParts);
        this.updateProgress(getDownloadedParts(), getNumberOfParts());
    }

    private void updateProgress(int counter, int total) {
        this.downloadProgress.setValue(counter * PROGRESS_MAX / total);
    }

    public int getDownloadedParts() {
        return this.tracker.getStatusCount(ProgressTracker.Status.DOWNLOADED);
    }

    public int getFailedParts() {
        return this.tracker.getStatusCount(ProgressTracker.Status.FAILED);
    }

    public BroadcastInformation getBroadcastInformation() {
        return this.bi;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getLengthRepr() {
        return timeFormatter.print(
                Period.seconds((int) this.bi.getVideoLength(TimeUnit.SECONDS)).normalizedStandard()
        );
    }

    public int getNumberOfMutedParts() {
        return this.bi.getSource().getNumberOfMutedParts();
    }

    public int getNumberOfParts() {
        return this.numberOfParts;
    }

    public long getFileSizeApproximation() {
        final int kBps300 = 300 * 1000; //kBps rate at 2.5 Mbps stream
        return this.bi.getVideoLength(TimeUnit.SECONDS) * kBps300;
    }

    public String getApproximateSizeRepr() {
        final long size = getFileSizeApproximation();

        if (size > 1000 * 1000) { //Mb
            return String.format("%d MB", size / (1000 * 1000));
        } else if (size > 1000) { //Kb
            return String.format("%d kB", size / 1000);
        } else { //b
            return String.format("%d B", size);
        }
    }

    public String getBroadcastAtRepr() {
        return dateFormatter.print(this.bi.getRecordedDate());
    }

    JProgressBar getDownloadProgress() {
        return this.downloadProgress;
    }

    public Runnable getDownloadTask(final VideoStoreTableView model, final int rowIdx) {
        try {
            Files.createDirectories(this.storageFolder.toPath());
            final ProgressTracker.ProgressUpdater tracker = new ProgressTracker.ProgressUpdater() {

                @Override
                public void updateProgress(int counter, int total) {
                    StoredBroadcast.this.updateProgress(counter, total);
                    model.fireTableCellUpdated(rowIdx, VideoStoreTableView.COLUMNS.DOWNLOAD_PROGRESS.getIdx());
                }

                @Override
                public void updateCount(ProgressTracker.Status type) {
                    switch (type) {
                        case DOWNLOADED:
                            model.fireTableCellUpdated(rowIdx, VideoStoreTableView.COLUMNS.DOWNLOADED_PARTS.getIdx());
                            break;
                        case FAILED:
                            model.fireTableCellUpdated(rowIdx, VideoStoreTableView.COLUMNS.FAILED_PARTS.getIdx());
                            break;
                    }
                }
            };

            this.tracker.reset(tracker);
            return this.bi.getSource().createDownloadTask(storageFolder, this.tracker);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
