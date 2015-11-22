package net.serverpeon.twitcharchiver

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import net.serverpeon.twitcharchiver.fx.data.TwitchMetadata
import net.serverpeon.twitcharchiver.fx.data.VodInformation
import net.serverpeon.twitcharchiver.fx.table.VideoModel
import net.serverpeon.twitcharchiver.fx.table.VideoTable
import java.time.Duration
import java.time.Instant

class Bootstrap : Application() {
    override fun start(stage: Stage) {
        val table = VideoTable()
        val testModel = VideoModel(VodInformation(
                TwitchMetadata(
                        title = "Hello Ama",
                        length = Duration.ofHours(2),
                        recordedAt = Instant.now(),
                        views = -2
                ),
                parts = 20,
                mutedParts = 5
        ))
        table.items.addAll(testModel, testModel.copy(progress = 1.2, partsAlreadyDownloaded = 20))

        stage.title = "Twitch-Archiver by @KiskaeEU"
        stage.scene = Scene(table)
        stage.show()
    }

}

fun main(args: Array<String>) {
    Application.launch(Bootstrap::class.java, *args)
}