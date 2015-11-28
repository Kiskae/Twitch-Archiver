package net.serverpeon.twitcharchiver.fx

import javafx.beans.property.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.network.DownloadableVod
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import java.nio.file.Path

class DownloadControl(val api: ApiWrapper, directory: ReadOnlyObjectProperty<Path?>) {
    private val isDownloading = SimpleBooleanProperty(false)
    val isDownloadingProp: ReadOnlyBooleanProperty
        get() = isDownloading

    private val downloadProgress = SimpleDoubleProperty(0.0)
    val downloadProgressProp: ReadOnlyDoubleProperty
        get() = downloadProgress

    val hasAccess = api.hasAccess(this)
    private var obs: AutoCloseable? = null

    fun beginDownload() {
        obs = api.lock(this)?.apply {
            isDownloading.set(true)
        }
    }

    fun stopDownload() {
        obs?.apply {
            close()
            obs = null
            isDownloading.set(false)
        }
    }

    fun createVideo(info: KrakenApi.VideoListResponse.Video, playlist: Playlist): DownloadableVod {
        return DownloadableVod(info, playlist)
    }
}