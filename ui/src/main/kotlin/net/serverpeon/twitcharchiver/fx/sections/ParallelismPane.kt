package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory
import javafx.scene.control.TitledPane
import net.serverpeon.twitcharchiver.fx.hbox

class ParallelismPane : TitledPane() {
    private val parallelism = SimpleIntegerProperty()
    val parallelismProp: ReadOnlyIntegerProperty
        get() = parallelism

    init {
        text = "Parallelism"

        content = hbox {
            +Label("Parallel Downloads").apply {
                padding = Insets(0.0, 5.0, 0.0, 0.0)
            }

            +Spinner(ParallelismSpinner).apply {
                parallelism.bind(valueProperty())
            }

            init {
                alignment = Pos.CENTER_RIGHT
            }
        }
    }

    object ParallelismSpinner : SpinnerValueFactory.IntegerSpinnerValueFactory(
            1,
            Math.max(MAX_PROCESSORS - 1, 1), //Reserve 1 thread for UI if possible
            Math.max(MAX_PROCESSORS - 1, 1) //Always default to all threads
    )

    companion object {
        private val MAX_PROCESSORS = Runtime.getRuntime().availableProcessors()
    }
}