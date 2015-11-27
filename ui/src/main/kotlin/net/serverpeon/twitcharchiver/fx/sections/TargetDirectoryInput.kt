package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.fx.vbox
import java.nio.file.Path

class TargetDirectoryInput : TitledPane() {
    private val directory = SimpleObjectProperty<Path?>(null)
    val directoryProp: ReadOnlyObjectProperty<Path?>
        get() = directory

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