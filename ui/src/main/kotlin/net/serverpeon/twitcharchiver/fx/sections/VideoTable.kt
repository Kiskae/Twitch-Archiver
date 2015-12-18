package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.binding.Bindings
import javafx.beans.binding.When
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.paint.Color
import javafx.scene.text.Text
import net.serverpeon.twitcharchiver.fx.*
import net.serverpeon.twitcharchiver.fx.merging.MergingDialog
import net.serverpeon.twitcharchiver.network.DownloadableVod
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import java.text.DecimalFormat
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.*

class VideoTable(val downloadControl: DownloadControl) : TableView<DownloadableVod>(), ChannelInput.VideoFeed {
    private val videos: ObservableList<DownloadableVod> = FXCollections.observableArrayList()

    override fun resetFeed() {
        this.videos.clear()
    }

    override fun insertVideo(info: KrakenApi.VideoListResponse.Video, playlist: Playlist) {
        this.videos.add(downloadControl.createVideo(info, playlist))
    }

    fun selectedVideos(): List<DownloadableVod> {
        return videos.filter { it.shouldDownload.value }
    }

    init {
        isEditable = true

        column("D.", { shouldDownload }) {
            editableProperty().bind(downloadControl.isDownloadingProp.not())

            renderer(CheckBox::class) { previousNode, newValue ->
                (previousNode ?: CheckBox().apply {
                    rowValue().shouldDownload.bind(selectedProperty())
                }).apply {
                    isSelected = newValue

                    disableProperty().bind(Bindings.not(
                            tableView.editableProperty().and(
                                    tableColumn.editableProperty())))
                }
            }

            applyToCell {
                alignment = Pos.CENTER
            }

            maxWidth = 3 * EM

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

            maxWidth = 4 * EM

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("DP", { downloadedParts }) {
            tooltip { "Downloaded parts" }

            maxWidth = 4 * EM

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("FP", { failedParts }) {
            tooltip { "Failed to download parts" }

            maxWidth = 4 * EM

            applyToCell {
                alignment = Pos.CENTER_RIGHT
            }
        }

        column("Download Progress", { downloadProgress }) {
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
                        else -> Button("Merge").apply {
                            padding = padding.let { oldPadding ->
                                Insets(0.0, 10.0, 0.0, 10.0)
                            }
                            onAction = EventHandler<javafx.event.ActionEvent> {
                                MergingDialog.show(tableView.items[tableRow.index].tracker.partFiles()!!)
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
            renderer(Label::class, { background = null }) { label, item ->
                val bg = if (item > 0) Color.RED else Color.GREEN
                background = Background(BackgroundFill(bg, null, Insets(1.0)))
                label ?: Label(item.toString())
            }

            maxWidth = 4 * EM

            applyToCell {
                alignment = Pos.CENTER
            }

            tooltip { "Muted parts" }
        }

        val tableComparator = comparatorProperty()
        val defaultComparator = Comparator.comparing<DownloadableVod, Instant> { it.recordedAt }.reversed()
        this.items = videos.sorted().apply {
            comparatorProperty().bind(
                    When(tableComparator.isNotNull)
                            .then(tableComparator)
                            .otherwise(defaultComparator)
            )
        }
    }

    companion object {
        private val EM: Double = Text("m").let {
            Scene(Group(it))
            it.applyCss()
            it.layoutBounds.width
        }

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