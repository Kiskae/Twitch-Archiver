package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.TitledPane
import javafx.stage.DirectoryChooser
import net.serverpeon.twitcharchiver.fx.bindTransform
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.fx.vbox
import java.nio.file.Path
import java.nio.file.Paths

class TargetDirectoryInput : TitledPane() {
    private val directory = SimpleObjectProperty<Path?>(null)
    val directoryProp: ReadOnlyObjectProperty<Path?>
        get() = directory

    init {
        text = "Target Directory"

        content = vbox {
            val boxWidth = widthProperty()

            +Label().apply {
                alignment = Pos.CENTER
                textOverrun = OverrunStyle.CENTER_ELLIPSIS
                textProperty().bindTransform(directory) {
                    if (it != null) {
                        it.toString()
                    } else {
                        "---"
                    }
                }

                boxWidth.addListener(object : ChangeListener<Number> {
                    override fun changed(observable: ObservableValue<out Number>, oldValue: Number, newValue: Number) {
                        // Ensure the label never causes the vbox to expand....
                        if (oldValue == 0.0 && newValue.toDouble() > 0.0) {
                            maxWidth = newValue.toDouble() - 40
                            boxWidth.removeListener(this)
                        }
                    }

                })
            }

            +Button("Change Directory").apply {
                stretch(Orientation.HORIZONTAL)
                onAction = EventHandler {
                    val directoryPicker = DirectoryChooser()
                    directoryPicker.title = "Choose download directory"
                    directoryPicker.initialDirectory = (directory.get() ?: Paths.get(System.getProperty("user.dir"))).toFile()
                    directoryPicker.showDialog(scene.window)?.let {
                        directory.set(it.toPath())
                    }
                }
            }
        }
    }
}