package net.serverpeon.twitcharchiver.fx.merging

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import com.google.common.io.CharStreams
import com.google.common.io.LineProcessor
import com.google.common.io.Resources
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ProgressBar
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Stage
import net.serverpeon.twitcharchiver.ReactiveFx
import net.serverpeon.twitcharchiver.fx.fxDSL
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.network.tracker.TrackerInfo
import net.serverpeon.twitcharchiver.twitch.playlist.EncodingDescription
import org.slf4j.LoggerFactory
import rx.Observable
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.function.BiPredicate
import kotlin.text.Regex

class MergingDialog(val segments: TrackerInfo.VideoSegments) : VBox() {
    val ffmpegProcessor = Observable.fromCallable {
        createProcess().start()
    }.flatMap { process ->
        Observable.create<String> { sub ->
            CharStreams.readLines(InputStreamReader(process.errorStream), object : LineProcessor<Unit> {
                override fun getResult() {
                    if (process.exitValue() != 0) {
                        sub.onError(RuntimeException("Non-zero exit: ${process.exitValue()}"))
                    } else {
                        sub.onCompleted()
                    }
                }

                override fun processLine(line: String): Boolean {
                    sub.onNext(line)
                    return true
                }
            })

            sub.add(Subscriptions.create {
                // Kill process on unsubscribe...
                if (process.isAlive)
                    process.destroyForcibly()
            })
        }
    }.doOnNext {
        FFMPEG_LOG.trace(it)
    }.skipWhile {
        //Skip until we find the first duration:
        //  Duration: 02:02:52.47, start: 64.010000, bitrate: 3735 kb/s
        !it.trimStart().startsWith("Duration:")
    }.lift(ExtractFirstOperator<Long, String> { firstDurationLine ->
        try {
            TOTAL_DURATION_REGEX.matchEntire(firstDurationLine)!!.groups[1]!!.value.toFfmpegDuration()
        } catch (ex: Exception) {
            -1 //
        }
    }).skipWhile {
        //Skip if we weren't able to parse the duration
        it.first == -1L
    }.filter {
        //Only process progress frames
        it.second.startsWith("frame=")
    }.map {
        val progressDuration = PROGRESS_DURATION_REGEX.matchEntire(it.second)!!.groups[1]!!.value.toFfmpegDuration()
        progressDuration.toDouble() / it.first
    }.subscribeOn(
            Schedulers.newThread() // Since process reading is blocking, run on a new thread
    ).observeOn(
            ReactiveFx.scheduler // But receive the updates on the application thread.
    )

    private val ffmpegPath = SimpleStringProperty("").apply {
        addListener { obs, old, new ->
            if (new.isNotEmpty()) {
                FFMPEG_LOG.info("FFMPEG path changed: {}", new)
            }
        }
    }
    private val activeWindow = Subscriptions.from()

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

            +hbox {
                +Text("ffmpeg not found, please manually select the installation directory:").apply {
                    visibleProperty().bind(ffmpegPath.isEmpty)
                }

                //TODO: add process to select ffmpeg
                +Button("")
            }

            +hbox {
                val progress = ProgressBar(0.0).apply {
                    stretch(Orientation.HORIZONTAL)
                }
                +progress

                +Button("Begin Merging").apply {
                    disableProperty().bind(ffmpegPath.isEmpty.or(merging))

                    onAction = EventHandler {
                        do {
                            if (segments.base.resolve("out.mp4").toFile().exists()) {
                                val result = Alert(Alert.AlertType.CONFIRMATION).apply {
                                    headerText = "There already is an out.mp4 file, only confirm if you want to override that file."
                                }.showAndWait()
                                if (result.orElse(null) != ButtonType.OK) {
                                    break
                                }
                            }
                            merging.value = true
                            val merger = ffmpegProcessor.doOnTerminate {
                                merging.value = false
                            }.doOnCompleted {
                                progress.progress = 1.0
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
        fun create(segments: TrackerInfo.VideoSegments): Stage {
            val pane = MergingDialog(segments)
            val stage = Stage()
            stage.scene = Scene(pane)
            stage.onCloseRequest = EventHandler {
                pane.onClose()
            }
            return stage
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
        return ProcessBuilder(fullCommand()).apply {
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