package net.serverpeon.twitcharchiver.fx.table

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import net.serverpeon.twitcharchiver.fx.data.VodInformation
import net.serverpeon.twitcharchiver.fx.network.DownloadService
import java.util.concurrent.ForkJoinPool

data class VideoModel(val vod: VodInformation, val partsAlreadyDownloaded: Int = 0, val progress: Double = 0.5) {
    private val downloadService: DownloadService = DownloadService()

    val downloadProgress: ReadOnlyDoubleProperty = SimpleDoubleProperty(0.0).apply {
        downloadService.progressProperty().addListener { obs, oldValue, newValue ->
            this.set(newValue.toDouble())
        }
    }

    val shouldDownloadProp: ObservableValue<Boolean> = SimpleBooleanProperty(partsAlreadyDownloaded != 0)
    val downloadedParts: ObservableValue<Int> = SimpleObjectProperty(partsAlreadyDownloaded)
    val failedParts: ObservableValue<Int> = SimpleObjectProperty(0)

    fun startDownload() {
        downloadService.executeOn(ForkJoinPool.commonPool())
    }
}