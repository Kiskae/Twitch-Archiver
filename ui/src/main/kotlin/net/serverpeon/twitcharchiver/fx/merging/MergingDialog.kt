package net.serverpeon.twitcharchiver.fx.merging

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.io.Resources
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import net.serverpeon.twitcharchiver.ReactiveFx
import net.serverpeon.twitcharchiver.fx.fxDSL
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.network.tracker.TrackerInfo
import net.serverpeon.twitcharchiver.twitch.playlist.EncodingDescription
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.function.BiPredicate
import kotlin.collections.map
import kotlin.text.*

class MergingDialog(val segments: TrackerInfo.VideoSegments) : VBox() {
    val ffmpegProcessor: Observable<Double> = Observable.fromCallable {
        ObservableProcess.create(createProcess())
    }.flatMap {
        it.observe {
            if (FFMPEG_LOG.isTraceEnabled) {
                errorStream.subscribe { FFMPEG_LOG.trace(it) }
            }

            val firstDuration: Observable<Long> = errorStream.takeFirst {
                // Find the line that represents total duration
                it.trimStart().startsWith("Duration:")
            }.map {
                TOTAL_DURATION_REGEX.matchEntire(it)!!.groups[1]!!.value.toFfmpegDuration()
            }

            val progressReports: Observable<Long> = errorStream.filter {
                // Only process progress frames
                it.startsWith("frame=")
            }.map {
                PROGRESS_DURATION_REGEX.matchEntire(it)!!.groups[1]!!.value.toFfmpegDuration()
            }

            firstDuration.flatMap { totalDuration ->
                progressReports.map { progress ->
                    progress.toDouble() / totalDuration
                }
            }
        }
    }.observeOn(
            ReactiveFx.scheduler // Receive updates on the fx thread
    )

    private val ffmpegPath = SimpleStringProperty("").apply {
        addListener { obs, old, new ->
            if (new.isNotEmpty()) {
                FFMPEG_LOG.info("FFMPEG path changed: {}", new)
            }
        }
    }
    private val activeWindow = CompositeSubscription()

    init {
        val presetPath: Path? = System.getProperty("ffmpeg.path", "").let {
            if (it.isEmpty()) null
            else findFfmpeg(Paths.get(it))
        }

        if (presetPath != null) {
            ffmpegPath.value = presetPath.toAbsolutePath().toString()
        }

        fxDSL(this) {
            val merging = SimpleBooleanProperty(false)

            +Text().apply {
                text = Resources.toString(Resources.getResource("ffmpeg-instructions.txt"), Charsets.UTF_8)
            }

            if (ffmpegPath.isEmpty.value) {
                +hbox {
                    init {
                        padding = Insets(10.0)
                        alignment = Pos.CENTER
                    }

                    +Text("ffmpeg not found, please manually select the installation directory: ")

                    +Button("Browse").apply {
                        disableProperty().bind(ffmpegPath.isNotEmpty)

                        onAction = EventHandler {
                            val dir: File? = DirectoryChooser().apply {
                                initialDirectory = File(".")
                            }.showDialog(scene.window)

                            dir?.let { findFfmpeg(it.toPath()) }?.apply {
                                ffmpegPath.set(this.toAbsolutePath().toString())
                                System.setProperty("ffmpeg.path", ffmpegPath.value)
                            }
                        }
                    }
                }
            }

            +hbox {
                +Text("ffmpeg path: ")

                +Label().apply {
                    textProperty().bind(ffmpegPath)
                    alignment = Pos.CENTER
                    textOverrun = OverrunStyle.CENTER_ELLIPSIS
                }
            }

            +hbox {
                val progress = ProgressBar(0.0).apply {
                    stretch(Orientation.HORIZONTAL)
                    padding = Insets(0.0, 5.0, 0.0, 0.0)
                }
                +progress

                +Button("Begin Merging").apply {
                    disableProperty().bind(ffmpegPath.isEmpty.or(merging))

                    onAction = EventHandler {
                        do {
                            merging.value = true
                            progress.progress = -1.0 // Default to undetermined
                            val merger = ffmpegProcessor.doOnTerminate {
                                merging.value = false
                            }.doOnCompleted {
                                progress.progress = 1.0

                                // Show alert when merging is done
                                Alert(Alert.AlertType.INFORMATION).apply {
                                    headerText = "Merging complete"
                                }.show()
                            }.subscribe({
                                progress.progress = it
                            }, { th ->
                                log.error("Exception during merging", th)
                            })

                            activeWindow.add(merger)
                        } while (false)
                    }
                }

                init {
                    alignment = Pos.CENTER
                    padding = Insets(5.0)
                }
            }

            init {
                padding = Insets(5.0)
            }
        }
    }

    private fun findFfmpeg(root: Path): Path? {
        if (!root.toFile().exists()) return null

        return Files.find(root, Int.MAX_VALUE, BiPredicate { path, attributes ->
            // Only match bin/ffmpeg.*
            path.fileName.toString().startsWith("ffmpeg") &&
                    path.parent.fileName.toString().equals("bin")
        }).findAny().orElse(null)
    }

    private fun String.toFfmpegDuration(): Long {
        val parts = this.split(SPLIT_FFMPEG_DURATION)
        return TimeUnit.HOURS.toMillis(parts[0].toLong()) +
                TimeUnit.MINUTES.toMillis(parts[1].toLong()) +
                TimeUnit.SECONDS.toMillis(parts[2].toLong()) +
                parts[3].toLong()
    }

    private fun ffmpegPath(): String {
        return ffmpegPath.get()
    }

    companion object {
        fun show(segments: TrackerInfo.VideoSegments) {
            val pane = MergingDialog(segments)
            val stage = Stage()
            stage.scene = Scene(pane)
            stage.title = "ffmpeg merge - Twitch Archiver - ${segments.base.fileName}"
            stage.show()
            stage.onCloseRequest = EventHandler {
                pane.onClose()
            }
        }

        //  Duration: 02:02:52.47, start: 64.010000, bitrate: 3735 kb/s
        private val TOTAL_DURATION_REGEX = Regex("\\s*Duration: ([^,]+).*")
        //frame=  960 fps=0.0 q=-1.0 Lsize=    7123kB time=00:00:16.00 bitrate=3647.0kbits/s speed= 492x
        private val PROGRESS_DURATION_REGEX = Regex(".*?time=([^\\s]+).*")
        private val SPLIT_FFMPEG_DURATION = Regex("[:\\.]")
        private val log = LoggerFactory.getLogger(MergingDialog::class.java)
        private val CONST_PARAMETERS = ImmutableList.of(
                "-y", //Allow overwrite on output
                "-nostdin", //Disable interaction
                "-c", "copy", // Only copy, don't convert
                "out.mp4" //Output to out.mp4
        )
        private val FFMPEG_LOG = LoggerFactory.getLogger("[FFMPEG]")
    }

    private fun onClose() {
        activeWindow.unsubscribe()
    }

    private fun createProcess(): ProcessBuilder {
        val cmd = fullCommand()
        log.debug("ffmpeg cmd: {}", cmd)
        return ProcessBuilder(cmd).apply {
            directory(segments.base.toFile()) //Run in parts directory
        }
    }

    private fun fullCommand(): List<String> {
        return ImmutableList.builder<String>()
                .add(ffmpegPath())
                .addAll(prepareOutput())
                .addAll(segments.encoding.parameters)
                .addAll(CONST_PARAMETERS)
                .build()
    }

    private fun prepareOutput(): List<String> {
        return when (segments.encoding.type) {
            EncodingDescription.IOType.INPUT_CONCAT -> {
                ImmutableList.of("-i", "concat:" + Joiner.on("|").join(segments.parts))
            }
            EncodingDescription.IOType.FILE_CONCAT -> {
                val tmpFile = File.createTempFile("ffmpeg", ".concat", segments.base.toFile())
                tmpFile.deleteOnExit()
                Files.write(tmpFile.toPath(), segments.parts.map { "file '$it'" })
                ImmutableList.of("-f", "concat", "-i", tmpFile.name)
            }
        }
    }
}