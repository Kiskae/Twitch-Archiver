package net.serverpeon.twitcharchiver.fx

import com.squareup.okhttp.OkHttpClient
import javafx.application.Platform
import javafx.beans.binding.When
import javafx.beans.property.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.network.DownloadableVod
import net.serverpeon.twitcharchiver.network.tracker.TrackerInfo
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicBoolean

class DownloadControl(val api: ApiWrapper,
                      val parentDirectory: ReadOnlyObjectProperty<Path?>,
                      val paralellism: ReadOnlyIntegerProperty,
                      val getVods: () -> List<DownloadableVod>) {
    private val isDownloading = SimpleBooleanProperty(false)
    val isDownloadingProp: ReadOnlyBooleanProperty
        get() = isDownloading

    private val downloadProgress = SimpleDoubleProperty(0.0).apply {
        bind(When(isDownloading).then(-1.0).otherwise(0.0))
    }
    val downloadProgressProp: ReadOnlyDoubleProperty
        get() = downloadProgress

    val hasAccess = api.hasAccess(this)

    private var downloadInProgress: CompletableFuture<*>? = null

    init {
        parentDirectory.addListener { obs, oldVal, newVal ->
            log.debug("Selected parent path changed: {}", newVal)
        }
    }

    fun beginDownload() {
        val vods = getVods().toArrayList()
        val client = OkHttpClient().apply {

        }

        check(null == downloadInProgress) {
            "Starting download while download is in progress?"
        }
        val apiLock = checkNotNull(api.lock(this)) {
            "Unable to exclusively lock api access"
        }
        isDownloading.set(true)

        val cancellation = AtomicBoolean(false)
        val future = ForkJoinTask.adapt {
            //Invoke all subtasks
            ForkJoinTask.invokeAll(vods.map {
                // Build a new downloader from the tracker
                it.tracker.newDownloader(client, cancellation)
            })
        }.toCompletableFuture(ForkJoinPool(paralellism.value)) //create a future for the download

        future.handle { value, throwable ->
            // Stop the download at earliest convenience
            if (throwable is CancellationException) {
                cancellation.set(true)
            }

            apiLock.close()
            Platform.runLater { isDownloading.set(false) }
            downloadInProgress = null
        }

        downloadInProgress = future
    }

    fun stopDownload() {
        downloadInProgress?.cancel(true)
        downloadInProgress = null
    }

    fun generateOutputDirectory(id: String, title: String): ReadOnlyObjectProperty<Path?> {
        return transformObservable(parentDirectory) {
            it?.resolve("$id-${TrackerInfo.sanitizeTitle(title)}")
        }
    }

    fun createVideo(info: KrakenApi.VideoListResponse.Video, playlist: Playlist): DownloadableVod {
        return DownloadableVod(info, TrackerInfo(generateOutputDirectory(info.internalId, info.title), playlist))
    }

    private fun <T> ForkJoinTask<T>.toCompletableFuture(pool: ForkJoinPool): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val task = pool.submit(ForkJoinTask.adapt {
            try {
                future.complete(this.invoke())
            } catch (ex: Exception) {
                future.completeExceptionally(ex)
            }
        })

        return future.handle { value, th ->
            task.cancel(true)
            pool.shutdownNow() //Completion of the future triggers a shutdown

            if (th != null) {
                throw th
            } else {
                value
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DownloadControl::class.java)
    }
}