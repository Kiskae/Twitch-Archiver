package net.serverpeon.twitcharchiver.fx.network

import com.google.common.collect.MapMaker
import java.util.*
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicLongFieldUpdater

class ConcurrentProgressTracker {
    @Volatile private var totalSize: Long = 0
    @Volatile private var downloadedSize: Long = 0
    @Volatile private var activeDownloads: Int = 0
    private val objectKeys: MutableSet<Any> = Collections.newSetFromMap(
            MapMaker().weakKeys().makeMap()
    )
    private var alreadyDownloaded = 0
    private var totalExpectedDownloads = -1

    fun addAndGetProgress(delta: Long, resolution: Long): Long = calculateProgress(resolution, updateProgress(delta))

    fun progress(resolution: Long): Long = calculateProgress(resolution, DOWNLOADED_SIZE_UPDATER.get(this))

    fun modifyExpectedTotal(delta: Long, key: Any) {
        TOTAL_SIZE_UPDATER.addAndGet(this, delta)
        if (objectKeys.add(key)) {
            ACTIVE_DOWNLOADS_UPDATER.incrementAndGet(this)
        }
    }

    private fun calculateProgress(resolution: Long, providedTotal: Long): Long {
        val totalSize = Math.max(TOTAL_SIZE_UPDATER.get(this), 1) //Ensure its always positive
        val activeDownloads = ACTIVE_DOWNLOADS_UPDATER.get(this)

        //RESOLUTION * (alreadyDownloaded / expectedDownloaded) +
        // RESOLUTION * (downloadedSize / totalSize) * (activeDownloads / expectedDownloads)
        //
        // a * (b / c) + a * (d / e) * (f / c)
        // a * (b * e + d * f) / (c * e)

        return (resolution * (alreadyDownloaded * totalSize + providedTotal * activeDownloads) / (totalSize * totalExpectedDownloads))
    }

    private fun updateProgress(delta: Long): Long {
        return DOWNLOADED_SIZE_UPDATER.addAndGet(this, delta)
    }

    override fun toString(): String {
        return "DownloadProgressBar{${progress(100)}/100}"
    }

    companion object {
        val TOTAL_SIZE_UPDATER = AtomicLongFieldUpdater
                .newUpdater(ConcurrentProgressTracker::class.java, "totalSize")
        val DOWNLOADED_SIZE_UPDATER = AtomicLongFieldUpdater
                .newUpdater(ConcurrentProgressTracker::class.java, "downloadedSize")
        val ACTIVE_DOWNLOADS_UPDATER = AtomicIntegerFieldUpdater
                .newUpdater(ConcurrentProgressTracker::class.java, "activeDownloads")
    }

    fun reset(alreadyDownloaded: Int, totalExpectedDownloads: Int) {
        this.alreadyDownloaded = alreadyDownloaded
        this.totalExpectedDownloads = totalExpectedDownloads
        TOTAL_SIZE_UPDATER.set(this, 0)
        DOWNLOADED_SIZE_UPDATER.set(this, 0)
        ACTIVE_DOWNLOADS_UPDATER.set(this, 0)
        this.objectKeys.clear()
    }
}