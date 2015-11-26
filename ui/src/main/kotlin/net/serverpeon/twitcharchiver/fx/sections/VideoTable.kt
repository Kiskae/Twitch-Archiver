package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.binding.Bindings
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.paint.Color
import net.serverpeon.twitcharchiver.fx.*
import net.serverpeon.twitcharchiver.network.DownloadableVod
import java.text.DecimalFormat
import java.text.MessageFormat
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField

class VideoTable : TableView<DownloadableVod>() {

    init {
        isEditable = true

        column("D.", { shouldDownload }) {
            isEditable = true

            renderer(CheckBox::class) { previousNode, newValue ->
                if (previousNode != null) {
                    previousNode.apply { isSelected = newValue }
                } else {
                    CheckBox().apply {
                        isSelected = newValue
                    }
                }.apply {
                    disableProperty().bind(Bindings.not(
                            tableView.editableProperty().and(
                                    tableColumn.editableProperty())))
                }
            }

            applyToCell {
                alignment = Pos.CENTER
            }

            tooltip { "Download this Video" }
        }

        column("Title", { title.toFxObservable() })

        column("Length", { length.toFxObservable() }) {
            textFormat { duration ->
                val hours = duration.toHours()
                val minutes = duration.minusHours(hours).toMinutes()
                val seconds = duration.minusMinutes(duration.toMinutes()).seconds
                DURATION_FORMATTER.get().format(arrayOf(hours, minutes, seconds))
            }
        }

        column("Views", { views.toFxObservable() }) {
            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("Approximate Size @ 2.5 Mbps", { approximateSize.toFxObservable() }) {
            textFormat { size ->
                if (size > BYTES_PER_MEGABYTE) {
                    "${size / BYTES_PER_MEGABYTE} MB"
                } else if (size > BYTES_PER_KILOBYTE) {
                    //Kb
                    "${size / BYTES_PER_KILOBYTE} kB"
                } else {
                    "$size B"
                }
            }

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("Recording Date", { recordedAt.toFxObservable() }) {
            textFormat { moment -> INSTANT_FORMATTER.format(moment) }
        }

        column("TP", { parts.toFxObservable() }) {
            tooltip { "Total parts" }

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("DP", { downloadedParts }) {
            tooltip { "Downloaded parts" }

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("FP", { failedParts }) {
            tooltip { "Failed to download parts" }

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("Download Progress", { downloadProgress.asObject() }) {
            renderer(Node::class) { previousNode, progress ->
                if (progress < 1) {
                    @Suppress("USELESS_CAST")
                    when (previousNode) {
                        is ProgressBar -> previousNode as ProgressBar
                        else -> ProgressBar()
                    }.apply {
                        this.progress = progress
                    }
                } else {
                    @Suppress("USELESS_CAST")
                    when (previousNode) {
                        is Button -> previousNode as Button
                        else -> Button("Merge Video").apply {
                            padding = padding.let { oldPadding ->
                                Insets(0.0, 10.0, 0.0, 10.0)
                            }
                            onAction = object : EventHandler<ActionEvent> {
                                override fun handle(event: ActionEvent?) {
                                    //TODO:
                                    println("Should download: ${tableView.items[tableRow.index]}")
                                    //tableView.items[tableRow.index].startDownload()
                                }
                            }
                        }
                    }
                }
            }

            applyToCell {
                alignment = Pos.CENTER
            }
        }

        column("MP", { mutedParts.toFxObservable() }) {
            renderer(Label::class) { label, item ->
                val bg = if (item > 0) Color.RED else Color.GREEN
                background = Background(BackgroundFill(bg, null, Insets(1.0)))
                label ?: Label(item.toString())
            }

            applyToCell {
                alignment = Pos.CENTER
            }

            tooltip { "Muted parts" }
        }

        this.items = FXCollections.observableArrayList()
    }

    companion object {
        private const val BYTES_PER_KILOBYTE = 1000
        private const val BYTES_PER_MEGABYTE = BYTES_PER_KILOBYTE * 1000

        private val DURATION_FORMATTER: ThreadLocal<MessageFormat> = object : ThreadLocal<MessageFormat>() {
            override fun initialValue(): MessageFormat? {
                return MessageFormat("{0}h{1}m{2}s").apply {
                    val formatter = DecimalFormat("00")
                    setFormatByArgumentIndex(1, formatter)
                    setFormatByArgumentIndex(2, formatter)
                }
            }
        }

        private val INSTANT_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
                .appendValue(ChronoField.DAY_OF_MONTH)
                .appendLiteral('-')
                .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
                .appendLiteral('-')
                .appendValue(ChronoField.YEAR)
                .appendLiteral(' ')
                .appendValue(ChronoField.HOUR_OF_DAY)
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR)
                .appendLiteral(' ')
                .appendZoneText(TextStyle.NARROW)
                .toFormatter()
                .withZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC))
    }
}