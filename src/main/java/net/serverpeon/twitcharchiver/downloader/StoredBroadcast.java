package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.serverpeon.twitcharchiver.twitch.BroadcastInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import javax.swing.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StoredBroadcast {
    private final static int PROGRESS_MAX = 100;
    private final JProgressBar downloadProgress = new JProgressBar(0, PROGRESS_MAX);
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final BroadcastInformation bi;
    private final File storageFolder;

    private final int numberOfParts;
    private int downloadedParts = 0;
    private int failedParts = 0;

    private boolean selected;
    private Map<String, Status> downloadStatus = Maps.newHashMap();

    StoredBroadcast(BroadcastInformation bi, File rootStorageDirectory) {
        this.bi = bi;
        this.storageFolder = new File(rootStorageDirectory, String.format(
                "%s-%s",
                bi.broadcastId,
                bi.title.replaceAll("[^a-zA-Z0-9\\.\\-]", "_") //Strip characters to make safe for filesystem
        ));
        this.selected = this.storageFolder.exists();
        this.numberOfParts = Iterables.size(this.bi.getSources());
        //Updated downloaded
        readStatusFile();
        updateProgressBar();
    }

    private static void downloadSource(
            BroadcastInformation.VideoSource vs,
            File targetFile,
            byte[] buffer,
            BiConsumer<Long, Long> progressUpdater
    ) throws IOException {
        targetFile.createNewFile();

        long totalRead = 0;
        logger.debug("Beginning download of {} to {}", vs.videoFileUrl, targetFile);

        final URLConnection urlConnection = new URL(vs.videoFileUrl).openConnection();
        final long reportedSize = urlConnection.getContentLengthLong();

        if (!urlConnection.getContentType().contains("video")) {
            throw new IOException(String.format(
                    "Twitch seems to be returning something that isn't video! (%s)",
                    urlConnection.getContentType()
            ));
        }

        try (final InputStream input = new BufferedInputStream(urlConnection.getInputStream())) {
            try (final OutputStream output = new FileOutputStream(targetFile)) {
                int readBytes;
                while ((readBytes = input.read(buffer)) > 0) {
                    output.write(buffer, 0, readBytes);
                    totalRead += readBytes;
                    progressUpdater.consume(totalRead, reportedSize);
                }
            }
        }

        if (totalRead < vs.length) {
            //Heuristic, if the video is less than 1bps, its probably corrupted/wrong
            throw new IOException(String.format(
                    "Invalid file size %d bytes, %d seconds",
                    totalRead,
                    vs.length
            ));
        }

        logger.debug("{} downloaded!", targetFile);
    }

    private void readStatusFile() {
        final File status = new File(this.storageFolder, "status.json");
        this.downloadStatus.clear();
        if (status.exists()) {
            try (final FileReader reader = new FileReader(status)) {
                final Map<?, ?> map = GSON.fromJson(reader, Map.class);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    this.downloadStatus.put(
                            entry.getKey().toString(),
                            Status.valueOf(entry.getValue().toString())
                    );
                }
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        //Update downloaded bytes
        for (final BroadcastInformation.VideoSource vs : this.getBroadcastInformation().getSources()) {
            if (this.downloadStatus.get(vs.videoFileUrl) == Status.DOWNLOADED) {
                this.downloadedParts++;
            }
        }
    }

    private void writeStatusFile() {
        final File status = new File(this.storageFolder, "status.json");
        try (final FileWriter writer = new FileWriter(status, false)) {
            GSON.toJson(this.downloadStatus, writer);
        } catch (IOException e) {
            logger.warn("Error saving!", e);
        }
    }

    private int getBaseProgressPercentage() {
        return getNumberOfParts() != 0 ? (downloadedParts * PROGRESS_MAX) / getNumberOfParts() : 0;
    }

    private void updateProgressBar() {
        final int percentage = getBaseProgressPercentage();
        this.downloadProgress.setValue(percentage);
    }

    public int getDownloadedParts() {
        return this.downloadedParts;
    }

    public int getFailedParts() {
        return this.failedParts;
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
        return FluentIterable.from(this.bi.getSources())
                .filter(new Predicate<BroadcastInformation.VideoSource>() {
                    @Override
                    public boolean apply(BroadcastInformation.VideoSource videoSource) {
                        return videoSource.muted;
                    }
                }).size();
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

    public void download(final VideoStoreTableView model, final int rowIdx, final int columnIdx) {
        final int progressPerPart = PROGRESS_MAX / this.getNumberOfParts();
        final byte buffer[] = new byte[1024 * 64]; //64kB

        this.storageFolder.mkdirs();

        logger.info("DOWNLOADING: {}", this.bi);

        int cursor = 0;
        for (final BroadcastInformation.VideoSource vs : this.bi.getSources()) {
            final File targetFile = new File(
                    this.storageFolder,
                    String.format(
                            "part%d.%s",
                            ++cursor,
                            Files.getFileExtension(vs.videoFileUrl)
                    )
            );

            if (this.downloadStatus.get(vs.videoFileUrl) == Status.DOWNLOADED && targetFile.exists()) continue;

            try {
                final int baseProgress = this.getBaseProgressPercentage();
                downloadSource(vs, targetFile, buffer, new BiConsumer<Long, Long>() {
                    private int lastProgress = Integer.MIN_VALUE;

                    @Override
                    public void consume(Long first, Long second) {
                        int newProgress = (int) (baseProgress + (first * progressPerPart / second));
                        if (newProgress > lastProgress) {
                            lastProgress = newProgress;
                            getDownloadProgress().setValue(newProgress);
                            model.fireTableCellUpdated(
                                    rowIdx,
                                    VideoStoreTableView.COLUMNS.DOWNLOAD_PROGRESS.getIdx()
                            );
                        }
                    }
                });
                this.downloadStatus.put(vs.videoFileUrl, Status.DOWNLOADED);
                this.downloadedParts++;
                model.fireTableCellUpdated(rowIdx, VideoStoreTableView.COLUMNS.DOWNLOADED_PARTS.getIdx());
            } catch (IOException ex) {
                logger.warn(new ParameterizedMessage("Error downloading {} to {}", vs.videoFileUrl, targetFile), ex);
                targetFile.delete();
                this.downloadStatus.put(vs.videoFileUrl, Status.FAILED);
                this.failedParts++;
                model.fireTableCellUpdated(rowIdx, VideoStoreTableView.COLUMNS.FAILED_PARTS.getIdx());
            }
            this.writeStatusFile();

            //Update download display
            this.updateProgressBar();
            model.fireTableCellUpdated(rowIdx, columnIdx);
        }
    }

    private enum Status {
        FAILED,
        DOWNLOADED
    }
}
