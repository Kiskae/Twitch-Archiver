package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.When
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ProgressBar
import net.serverpeon.twitcharchiver.fx.DownloadControl
import net.serverpeon.twitcharchiver.fx.NodeDelegate
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch

class DownloadPane(downloadControl: DownloadControl, pathSelected: BooleanBinding) : NodeDelegate() {
    init {
        content = hbox {
            +ProgressBar(0.0).apply {
                stretch(Orientation.HORIZONTAL)
                padding = Insets(0.0, 5.0, 0.0, 0.0)
                progressProperty().bind(
                        When(downloadControl.isDownloadingProp)
                                .then(downloadControl.downloadProgressProp)
                                .otherwise(0.0)
                )
            }

            +Button().apply {
                textProperty().bind(
                        When(downloadControl.isDownloadingProp.not())
                                .then("Download Selected Broadcasts")
                                .otherwise("Cancel"))

                onActionProperty().bind(
                        When(downloadControl.isDownloadingProp.not())
                                .then(EventHandler<ActionEvent> {
                                    downloadControl.beginDownload()
                                })
                                .otherwise(EventHandler<ActionEvent> {
                                    downloadControl.stopDownload()
                                })
                )
            }

            init {
                alignment = Pos.CENTER
                padding = Insets(5.0)
            }
        }

        content.disableProperty().bind(downloadControl.hasAccess.not().or(pathSelected.not()))
    }
}