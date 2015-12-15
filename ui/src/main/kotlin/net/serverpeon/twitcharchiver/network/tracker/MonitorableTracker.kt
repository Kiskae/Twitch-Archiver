package net.serverpeon.twitcharchiver.network.tracker

import com.squareup.okhttp.Response
import javafx.application.Platform
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import net.serverpeon.twitcharchiver.network.ConcurrentProgressTracker
import net.serverpeon.twitcharchiver.network.download.DownloadSteward
import net.serverpeon.twitcharchiver.network.download.ForkJoinDownloader
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MonitorableTracker(val wasCancelled: AtomicBoolean,
                         val statusLog: ObservableStatusLog,
                         partsToDownload: Int,
                         totalParts: Int
) : DownloadSteward<Int> {
    private val progressTracker = ConcurrentProgressTracker().apply {
        reset(totalParts - partsToDownload, totalParts)
    }
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
        log.debug("Starting download of {} to {}", response.request().urlString(), entry.sink)
    }

    override fun validatePost(entry: ForkJoinDownloader.DownloadEntry<Int>, response: Response, totalBytesDownloaded: Long) {
        //FIXME: heuristic validation?
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
        private val log = LoggerFactory.getLogger(MonitorableTracker::class.java)
    }
}