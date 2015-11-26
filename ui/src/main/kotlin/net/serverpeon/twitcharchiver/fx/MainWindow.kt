package net.serverpeon.twitcharchiver.fx

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import net.serverpeon.twitcharchiver.fx.sections.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.twitch.OAuthToken
import net.serverpeon.twitcharchiver.twitch.TwitchApi

class MainWindow(token: OAuthToken) : BorderPane() {
    private val api = ApiWrapper(TwitchApi(token))

    init {
        val infoTable = VideoTable()
        val isDownloading = SimpleBooleanProperty(false)
        val downloadPane = DownloadPane(api, isDownloading, SimpleDoubleProperty(-1.0))

        downloadPane.shouldDownloadProp.addListener { obs, old, new ->
            println("Should download: $new")
            isDownloading.set(new)
        }

        center = infoTable
        bottom = downloadPane.node()
        right = createControlPane(token)
    }

    private fun createControlPane(token: OAuthToken): Node {
        val sectionPadding = Insets(5.0, 5.0, 0.0, 5.0)
        return vbox {
            val oauth = OAuthInput(api, token)
            +oauth

            oauth.usernameProp.addListener { observableValue, old, new ->
                println("$old -> $new")
            }

            +ChannelInput(oauth.usernameProp)

            +TargetDirectoryInput()

            forEach<Region> {
                padding = sectionPadding
            }
        }
    }
}