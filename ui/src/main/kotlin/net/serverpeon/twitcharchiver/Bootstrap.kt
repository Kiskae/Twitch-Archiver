package net.serverpeon.twitcharchiver

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.stage.Stage
import net.serverpeon.twitcharchiver.fx.MainWindow
import net.serverpeon.twitcharchiver.twitch.OAuthToken
import org.slf4j.LoggerFactory

class Bootstrap : Application() {
    override fun stop() {
        System.exit(0)
    }

    override fun start(stage: Stage) {
        try {
            val token = OAuthToken(parameters.named["oauth"])

            stage.title = "Twitch-Archiver by @KiskaeEU"
            stage.scene = Scene(MainWindow(token))
            stage.show()
        } catch (e: Throwable) {
            LoggerFactory.getLogger(javaClass).error("Exception during window creation", e)
        }
    }

}

fun main(args: Array<String>) {
    Platform.setImplicitExit(true)
    Application.launch(Bootstrap::class.java, *args)
}