package net.serverpeon.twitcharchiver.network

import javafx.beans.property.*
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import java.time.Duration
import java.time.Instant

data class DownloadableVod(val twitchData: KrakenApi.VideoListResponse.Video, val playlist: Playlist) { //TODO: LocalData
    val downloadProgress: ReadOnlyDoubleProperty = SimpleDoubleProperty(0.0) // Link to download service
    val shouldDownload: ReadOnlyBooleanProperty = SimpleBooleanProperty() // Used to select videos
    val downloadedParts: ReadOnlyIntegerProperty = SimpleIntegerProperty(0) // Link to local data?
    val failedParts: ReadOnlyIntegerProperty = SimpleIntegerProperty(0) // Link to local data

    val title: String
        get() = twitchData.title

    val length: Duration
        get() = twitchData.length

    val views: Long
        get() = twitchData.views

    val approximateSize: Long
        get() {
            val kBps300 = 300 * 1000 //kBps rate at 2.5 Mbps stream
            return length.seconds * kBps300
        }

    val recordedAt: Instant
        get() = twitchData.recordedAt.toInstant()

    val parts: Int
        get() = playlist.parts()

    val mutedParts: Int
        get() = playlist.mutedParts()
}