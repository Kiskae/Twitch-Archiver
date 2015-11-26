package net.serverpeon.twitcharchiver

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.stage.Stage
import net.serverpeon.twitcharchiver.fx.MainWindow
import net.serverpeon.twitcharchiver.twitch.OAuthToken

class Bootstrap : Application() {

    override fun start(stage: Stage) {
        val token = OAuthToken(parameters.named["oauth"])

        stage.title = "Twitch-Archiver by @KiskaeEU"
        stage.scene = Scene(MainWindow(token))
        stage.show()
    }

}

fun main(args: Array<String>) {
    Platform.setImplicitExit(true)
    Application.launch(Bootstrap::class.java, *args)
}