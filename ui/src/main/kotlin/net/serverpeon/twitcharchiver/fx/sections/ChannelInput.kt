package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.Region
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.fx.vbox

class ChannelInput(username: ReadOnlyStringProperty) : TitledPane() {
    private val videoLimit = SimpleIntegerProperty()
    private val retrieveVideos = SimpleBooleanProperty(false)

    val videoLimitProp: ReadOnlyIntegerProperty
        get() = videoLimit

    init {
        text = "Channel"

        content = vbox {
            +hbox {
                +Label("Channel Name").apply {
                    padding = Insets(0.0, 5.0, 0.0, 0.0)
                }
                +TextField().apply {
                    textProperty().bind(username)
                    disableProperty().set(true)
                }

                init {
                    alignment = Pos.CENTER_RIGHT
                }
            }

            +hbox {
                +Label("Video Limit").apply {
                    padding = Insets(0.0, 5.0, 0.0, 0.0)
                }
                +Spinner(NonZeroSpinner(-1, Int.MAX_VALUE)).apply {
                    videoLimit.bind(valueProperty())
                }

                init {
                    alignment = Pos.CENTER_RIGHT
                }
            }

            +vbox {
                +Button("Query Twitch for Videos").apply {
                    stretch(Orientation.HORIZONTAL)
                }

                +ProgressBar(0.0).apply {
                    stretch(Orientation.HORIZONTAL)
                }
            }

            forEach<Region> {
                padding = Insets(3.0)
            }
        }

        disableProperty().bind(username.isNull)
    }

    class NonZeroSpinner(min: Int, max: Int) : SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, min) {
        override fun increment(steps: Int) {
            super.increment(steps)
            if (value == 0) {
                super.increment(1)
            }
        }

        override fun decrement(steps: Int) {
            super.decrement(steps)
            if (value == 0) {
                super.decrement(1)
            }
        }
    }
}