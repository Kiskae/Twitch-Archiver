package net.serverpeon.twitcharchiver.network.tracker

import javafx.application.Platform
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import net.serverpeon.twitcharchiver.network.ConcurrentProgressTracker
import net.serverpeon.twitcharchiver.network.download.DownloadSteward
import net.serverpeon.twitcharchiver.network.download.ForkJoinDownloader
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MonitorableTracker(val wasCancelled: AtomicBoolean,
                         val statusLog: ObservableStatusLog,
                         partsToDownload: Int,
                         val playlist: Playlist
) : DownloadSteward<Int> {
    private val progressTracker = ConcurrentProgressTracker().apply {
        reset(playlist.videos.size - partsToDownload, playlist.videos.size)
    }
    private val log = LoggerFactory.getLogger("${MonitorableTracker::class.java.name}.${playlist.broadcastId}")
    private val currentProgress = AtomicLong(-1)
    private val isUpdating = AtomicBoolean(false)
    private val progressProp = SimpleDoubleProperty(calculateProgress(progressTracker.progress(PROGRESS_RESOLUTION)))

    val readOnlyProgressProp: ReadOnlyDoubleProperty
        get() = progressProp

    private fun calculateProgress(progress: Long): Double {
        return progress.toDouble() / PROGRESS_RESOLUTION
    }

    override fun validatePre(entry: ForkJoinDownloader.DownloadEntry<Int>, response: Response) {
        check("video" == response.body().contentType().type()) {
            "Request returned non-video content (${response.body().contentType()})"
        }
        progressTracker.modifyExpectedTotal(response.body().contentLength(), entry.ident)
        log.debug("Starting download of {} to {}", response.request().url().toString(), entry.sink)
    }

    override fun validatePost(entry: ForkJoinDownloader.DownloadEntry<Int>, response: Response, totalBytesDownloaded: Long) {
        //Heuristic, if the video is less than 1bps, its probably corrupted/wrong
        if (totalBytesDownloaded == 0L) {
            log.warn("Twitch sent an empty file for part{}", entry.ident)
        } else if (playlist.length.seconds > totalBytesDownloaded) {
            throw IOException("Invalid file size $totalBytesDownloaded, ${playlist.length}")
        }

        log.debug("Finished download of {}", entry.sink)
    }

    override fun onBegin(entry: ForkJoinDownloader.DownloadEntry<Int>) {
        statusLog[entry.ident] = null //reset progress

        checkCancelled()
    }

    override fun onUpdate(deltaSinceLastUpdate: Int) {
        checkCancelled()

        val progress = progressTracker.addAndGetProgress(
                deltaSinceLastUpdate.toLong(),
                PROGRESS_RESOLUTION
        )

        do {
            val oldProgress = currentProgress.get()

            if (oldProgress >= progress) {
                return //Break progress
            }
        } while (!currentProgress.compareAndSet(oldProgress, progress))

        requestUpdate()
    }

    private fun requestUpdate() {
        if (isUpdating.compareAndSet(false, true)) {
            Platform.runLater {
                isUpdating.set(false)
                progressProp.set(calculateProgress(currentProgress.get()))
            }
        }
    }

    private fun checkCancelled() {
        if (wasCancelled.get()) {
            throw CancellationException()
        }
    }

    override fun onEnd(entry: ForkJoinDownloader.DownloadEntry<Int>) {
        statusLog[entry.ident] = StatusLogPersistence.Status.DOWNLOADED
    }

    override fun onException(entry: ForkJoinDownloader.DownloadEntry<Int>, th: Throwable) {
        if (th !is CancellationException) {
            // Its only an exception if its not cancelled
            statusLog[entry.ident] = StatusLogPersistence.Status.FAILED
            log.error("Error downloading {}", entry.sink, th)
        }
    }

    companion object {
        private const val PROGRESS_RESOLUTION: Long = 200
    }
}