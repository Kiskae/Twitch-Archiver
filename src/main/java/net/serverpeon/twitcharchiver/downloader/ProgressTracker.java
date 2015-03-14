package net.serverpeon.twitcharchiver.downloader;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ProgressTracker {
    private final static Logger logger = LogManager.getLogger(ProgressTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Status> currentStatus = Maps.newHashMap();
    private final ReentrantReadWriteLock mapLock = new ReentrantReadWriteLock();

    private final File trackerFile;
    private final int numberOfParts;
    private ProgressUpdater updater = null;

    public ProgressTracker(File storageFolder, int numberOfParts) {
        this.numberOfParts = numberOfParts;
        this.trackerFile = new File(storageFolder, "status.json");

        if (trackerFile.exists()) {
            try (final FileReader reader = new FileReader(trackerFile)) {
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
        return new Partial(ident, updater);
    }

    private void write() {
        mapLock.readLock().lock();
        try {
            try (final FileWriter writer = new FileWriter(trackerFile, false)) {
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
        private final String identifier;
        private final ProgressUpdater updater;

        Partial(final String identifier, ProgressUpdater updater) {
            this.identifier = identifier;
            this.updater = updater;
        }

        public void update(final long soFar, final long expectedTotal) {
            //updater.updateProgress(soFar, expectedTotal);
        }

        private int count() {
            return getStatusCount(Status.DOWNLOADED);
        }

        public void invalidate() {
            mapLock.writeLock().lock();
            try {
                currentStatus.put(identifier, Status.FAILED);
                updater.updateCount(Status.FAILED);
                updater.updateProgress(count(), numberOfParts);
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
                updater.updateProgress(count(), numberOfParts);
                write();
            } finally {
                mapLock.writeLock().unlock();
            }
        }
    }
}
