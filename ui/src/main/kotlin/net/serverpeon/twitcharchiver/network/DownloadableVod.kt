package net.serverpeon.twitcharchiver.network

import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.ObservableValue
import net.serverpeon.twitcharchiver.network.tracker.TrackerInfo
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import java.time.Duration
import java.time.Instant

data class DownloadableVod(val twitchData: KrakenApi.VideoListResponse.Video,
                           val tracker: TrackerInfo) {
    val shouldDownload: BooleanProperty = SimpleBooleanProperty(false)

    val downloadProgress: ObservableValue<Double>
        get() = tracker.downloadProgressProp.asObject()

    val downloadedParts: ObservableValue<Int>
        get() = tracker.downloadedParts.asObject()

    val failedParts: ObservableValue<Int>
        get() = tracker.failedParts

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
        get() = tracker.playlist.parts()

    val mutedParts: Int
        get() = tracker.playlist.mutedParts()
}