package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ProgressTracker {
    private final static Logger logger = LogManager.getLogger(ProgressTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Status> currentStatus = Maps.newHashMap();
    private final ReentrantReadWriteLock mapLock = new ReentrantReadWriteLock();

    private final File trackerFile;
    private final int numberOfParts;
    private final ConcurrentProgressBarTracker pBar = new ConcurrentProgressBarTracker();
    private final Map<String, Partial> partials = Maps.newHashMap();
    private ProgressUpdater updater = null;

    public ProgressTracker(File storageFolder, int numberOfParts) {
        this.numberOfParts = numberOfParts;
        this.trackerFile = new File(storageFolder, "status.json");

        if (trackerFile.exists()) {

            try (final InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(trackerFile),
                    Charsets.UTF_8
            )) {
                final Map<?, ?> map = GSON.fromJson(reader, Map.class);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    this.currentStatus.put(
                            entry.getKey().toString(),
                            Status.valueOf(entry.getValue().toString())
                    );
                }
            } catch (IOException e) {
                logger.debug("Error reading!", e);
            }

            this.reset(null); //Reset 'failed' entries
        }
    }

    public void reset(final ProgressUpdater updater) {
        this.updater = updater;

        //Clean up downloading state
        mapLock.writeLock().lock();
        try {
            Iterables.removeIf(currentStatus.values(), new Predicate<Status>() {
                @Override
                public boolean apply(Status status) {
                    return status != Status.DOWNLOADED;
                }
            });
        } finally {
            mapLock.writeLock().unlock();
        }

        //Reset progress bar tracker
        this.pBar.reset(getStatusCount(Status.DOWNLOADED), numberOfParts);

        //Reset total so the progress bar is correctly updated when download resumes
        for (Partial p : partials.values()) {
            p.storedTotal = 0;
        }
    }

    public Status getStatus(final String ident) {
        final Status status = currentStatus.get(ident);
        return status != null ? status : Status.UNTRACKED;
    }

    public int getStatusCount(final Status status) {
        mapLock.readLock().lock();
        try {
            return Maps.filterValues(currentStatus, new Predicate<Status>() {
                @Override
                public boolean apply(Status s) {
                    return status == s;
                }
            }).size();
        } finally {
            mapLock.readLock().unlock();
        }
    }

    public Partial track(final String ident) {
        if (partials.containsKey(ident)) {
            return partials.get(ident);
        } else {
            final Partial p = new Partial(ident);
            partials.put(ident, p);
            return p;
        }
    }

    private void write() {
        mapLock.readLock().lock();
        try {

            try (final OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(trackerFile, false),
                    Charsets.UTF_8
            )) {
                GSON.toJson(this.currentStatus, writer);
            } catch (IOException e) {
                logger.warn("Error saving!", e);
            }
        } finally {
            mapLock.readLock().unlock();
        }
    }

    public enum Status {
        UNTRACKED,
        FAILED,
        DOWNLOADED
    }

    public interface ProgressUpdater {
        void updateProgress(int counter, int total);

        void updateCount(Status type);
    }

    public class Partial {
        private final static int PROGRESS_RESOLUTION = 1000;
        private final int latestProgress = -1;
        private final Object pKey = new Object();
        private final String identifier;
        private long storedTotal = 0;
        private long soFar = 0;

        Partial(final String identifier) {
            this.identifier = identifier;
        }

        public void update(final long soFar, final long expectedTotal) {
            if (storedTotal != expectedTotal) {
                pBar.modifyExpectedTotal(expectedTotal - storedTotal, pKey);
                storedTotal = expectedTotal;
            }


            long addedDownload = Math.min(soFar, expectedTotal) - this.soFar;
            this.soFar += addedDownload;
            int progress = pBar.updateAndGetProgress(addedDownload, PROGRESS_RESOLUTION);
            if (progress != latestProgress) {
                updater.updateProgress(progress, PROGRESS_RESOLUTION);
            }
        }

        public void invalidate() {
            mapLock.writeLock().lock();
            try {
                currentStatus.put(identifier, Status.FAILED);
                updater.updateCount(Status.FAILED);

                //Reverse all download progress
                updater.updateProgress(
                        pBar.updateAndGetProgress(-1 * this.soFar, PROGRESS_RESOLUTION),
                        PROGRESS_RESOLUTION
                );
                write();
            } finally {
                mapLock.writeLock().unlock();
            }
        }

        public void finish() {
            mapLock.writeLock().lock();
            try {
                currentStatus.put(identifier, Status.DOWNLOADED);
                updater.updateCount(Status.DOWNLOADED);

                //Make sure the the total file has been registered as downloaded
                updater.updateProgress(
                        pBar.updateAndGetProgress(storedTotal - soFar, PROGRESS_RESOLUTION),
                        PROGRESS_RESOLUTION
                );

                write();
            } finally {
                mapLock.writeLock().unlock();
            }
        }
    }
}
