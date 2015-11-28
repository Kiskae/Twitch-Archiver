package net.serverpeon.twitcharchiver.fx

import javafx.beans.property.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.network.DownloadableVod
import net.serverpeon.twitcharchiver.network.tracker.TrackerInfo
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import java.nio.file.Path

class DownloadControl(val api: ApiWrapper,
                      directory: ReadOnlyObjectProperty<Path?>,
                      paralellism: ReadOnlyIntegerProperty,
                      val getVods: () -> List<DownloadableVod>) {
    private val isDownloading = SimpleBooleanProperty(false)
    val isDownloadingProp: ReadOnlyBooleanProperty
        get() = isDownloading

    private val downloadProgress = SimpleDoubleProperty(0.0)
    val downloadProgressProp: ReadOnlyDoubleProperty
        get() = downloadProgress

    val hasAccess = api.hasAccess(this)

    fun beginDownload() {
        println(getVods().toArrayList())
    }

    fun stopDownload() {
    }

    fun createVideo(info: KrakenApi.VideoListResponse.Video, playlist: Playlist): DownloadableVod {
        return DownloadableVod(info, playlist, TrackerInfo())
    }
}