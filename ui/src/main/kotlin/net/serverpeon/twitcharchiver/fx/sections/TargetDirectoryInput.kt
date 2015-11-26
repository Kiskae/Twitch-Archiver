package net.serverpeon.twitcharchiver.fx.sections

import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.fx.vbox

class TargetDirectoryInput : TitledPane() {
    init {
        text = "Target Directory"

        content = vbox {
            +TextField().apply {
                stretch(Orientation.HORIZONTAL)
            }

            +Button("Change Directory").apply {
                stretch(Orientation.HORIZONTAL)
            }
        }
    }
}