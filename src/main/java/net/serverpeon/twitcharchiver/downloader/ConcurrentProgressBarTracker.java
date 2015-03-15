package net.serverpeon.twitcharchiver.downloader;

import com.google.common.collect.Sets;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ConcurrentProgressBarTracker {
    private final AtomicLong totalSize = new AtomicLong(0);
    private final AtomicLong downloadedSize = new AtomicLong(0);
    private final AtomicInteger activeDownloads = new AtomicInteger(0);
    private final Set<Object> objectKeys = Sets.newConcurrentHashSet();
    private int alreadyDownloaded = 0;
    private int totalExpectedDownloads = -1;

    public ConcurrentProgressBarTracker() {
    }

    public void reset(int alreadyDownloaded, int totalExpectedDownloads) {
        this.alreadyDownloaded = alreadyDownloaded;
        this.totalExpectedDownloads = totalExpectedDownloads;
        this.totalSize.set(0);
        this.downloadedSize.set(0);
        this.activeDownloads.set(0);
    }

    public int updateAndGetProgress(long delta, int resolution) {
        return getProgress(resolution, updateProgress(delta));
    }

    public int getProgress(final int resolution) {
        return getProgress(resolution, downloadedSize.get());
    }

    private int getProgress(int resolution, long latestTotalSoFar) {
        final long totalSize = Math.max(this.totalSize.get(), 1); //Ensure its always non-negative
        final long activeDownloads = this.activeDownloads.get();

        //RESOLUTION * (alreadyDownloaded / expectedDownloaded) +
        // RESOLUTION * (downloadedSize / totalSize) * (activeDownloads / expectedDownloads)
        //
        // a * (b / c) + a * (d / e) * (f / c)
        // a * (b * e + d * f) / (c * e)

        return (int) (resolution *
                (alreadyDownloaded * totalSize + latestTotalSoFar * activeDownloads)
                / (totalSize * totalExpectedDownloads));
    }

    public void modifyExpectedTotal(long delta, Object key) {
        this.totalSize.addAndGet(delta);
        if (!objectKeys.contains(key)) {
            objectKeys.add(key);
            this.activeDownloads.incrementAndGet();
        }
    }

    public long updateProgress(long delta) {
        return this.downloadedSize.addAndGet(delta);
    }
}
