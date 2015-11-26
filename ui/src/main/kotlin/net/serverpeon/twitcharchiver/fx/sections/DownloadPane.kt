package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.binding.BooleanExpression
import javafx.beans.binding.DoubleExpression
import javafx.beans.binding.When
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ProgressBar
import net.serverpeon.twitcharchiver.fx.NodeDelegate
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.network.ApiWrapper

class DownloadPane(private val api: ApiWrapper, isDownloading: BooleanExpression, downloadProgress: DoubleExpression) : NodeDelegate() {
    private val shouldDownload = SimpleBooleanProperty(false)

    val apiBinding = api.hasAccess(this)
    val shouldDownloadProp: ReadOnlyBooleanProperty
        get() = shouldDownload

    init {
        isDownloading.addListener { obs, oldVal, newVal ->
            if (oldVal && !newVal) {
                shouldDownload.set(false)
            }
        }

        content = hbox {
            +ProgressBar(0.0).apply {
                stretch(Orientation.HORIZONTAL)
                padding = Insets(0.0, 5.0, 0.0, 0.0)
                progressProperty().bind(When(isDownloading).then(downloadProgress).otherwise(0.0))
            }

            +Button().apply {
                textProperty().bind(When(isDownloading).then("Cancel").otherwise("Download Selected Broadcasts"))

                onActionProperty().bind(When(isDownloading.not()).then(EventHandler<ActionEvent> {
                    shouldDownload.set(true)
                }).otherwise(EventHandler<ActionEvent> {
                    shouldDownload.set(false)
                }))
            }

            init {
                alignment = Pos.CENTER
                padding = Insets(5.0)
            }
        }
    }
}