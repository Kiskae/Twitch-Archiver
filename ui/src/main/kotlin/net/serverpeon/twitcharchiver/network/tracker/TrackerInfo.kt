package net.serverpeon.twitcharchiver.network.tracker

import com.google.common.io.Files
import com.squareup.okhttp.Call
import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.value.ObservableValue
import net.serverpeon.twitcharchiver.network.download.ForkJoinDownloader
import net.serverpeon.twitcharchiver.twitch.playlist.EncodingDescription
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Regex

class TrackerInfo(val dataDirectory: ReadOnlyObjectProperty<Path?>, val playlist: Playlist) {
    private val statusLog = ObservableStatusLog(dataDirectory)

    val downloadedParts: SimpleIntegerProperty = SimpleIntegerProperty(0).apply {
        bind(statusLog.createProperty {
            val dir = dataDirectory.get()
            if (dir != null) {
                it.count {
                    it.value == StatusLogPersistence.Status.DOWNLOADED &&
                            dir.resolve(playlist.videos[it.key].toFilename(it.key))
                                    .toFile()
                                    .exists()
                }
            } else {
                0
            }
        })
    }
    val failedParts: ObservableValue<Int> = SimpleIntegerProperty(0).apply {
        bind(statusLog.createProperty {
            it.count {
                it.value == StatusLogPersistence.Status.FAILED
            }
        })
    }.asObject()

    private val downloadProgress = SimpleDoubleProperty(0.0).apply {
        bind(downloadedParts.divide(playlist.parts().toDouble()))
    }
    val downloadProgressProp: ReadOnlyDoubleProperty
        get() = downloadProgress

    fun newDownloader(client: OkHttpClient, cancellation: AtomicBoolean): ForkJoinTask<*> {
        val directory = checkNotNull(dataDirectory.value)

        directory.toFile().apply {
            if (!exists()) {
                check(mkdirs()) { "Failed to create directory for the video files: $this" }
            }
        }

        val partsToDownload = playlist.videos.mapIndexed { idx, video ->
            ForkJoinDownloader.DownloadEntry(
                    mapUrlToCall(client, video.resource),
                    directory.resolve(video.toFilename(idx)),
                    idx
            )
        }.filter {
            statusLog[it.ident] != StatusLogPersistence.Status.DOWNLOADED || !it.sink.toFile().exists()
        }

        val monitor = MonitorableTracker(cancellation, statusLog, partsToDownload.size, playlist)
        downloadProgress.bind(monitor.readOnlyProgressProp)

        return ForkJoinDownloader.create(partsToDownload, monitor)
    }

    data class VideoSegments(val base: Path, val parts: List<String>, val encoding: EncodingDescription)

    fun partFiles(): VideoSegments? {
        return dataDirectory.get()?.let { directory ->
            VideoSegments(directory, playlist.videos.mapIndexed { idx, video ->
                video.toFilename(idx)
            }, playlist.encoding)
        }
    }

    private fun Playlist.Video.toFilename(idx: Int): String {
        val ext = Files.getFileExtension(this.resource.encodedPath())
        return "part$idx.$ext"
    }

    private fun mapUrlToCall(client: OkHttpClient, url: HttpUrl): Call {
        val request = Request.Builder()
                .url(url)
                .get()
                .build()

        return client.newCall(request)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrackerInfo::class.java)
        private val SAFE_CHARACTERS = Regex("[^-a-zA-Z0-9]")

        fun sanitizeTitle(title: String): String {
            return title.replace(SAFE_CHARACTERS, "_")
        }
    }
}