package net.serverpeon.twitcharchiver.network

import javafx.beans.property.BooleanProperty
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.SimpleBooleanProperty
import net.serverpeon.twitcharchiver.network.tracker.TrackerInfo
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import java.time.Duration
import java.time.Instant

data class DownloadableVod(val twitchData: KrakenApi.VideoListResponse.Video,
                           val playlist: Playlist,
                           val tracker: TrackerInfo) {
    val shouldDownload: BooleanProperty = SimpleBooleanProperty(tracker.hasPriorData)

    val downloadProgress: ReadOnlyDoubleProperty
        get() = tracker.downloadProgressProp

    val downloadedParts: ReadOnlyIntegerProperty
        get() = tracker.downloadedPartsProp

    val failedParts: ReadOnlyIntegerProperty
        get() = tracker.failedPartsProp

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