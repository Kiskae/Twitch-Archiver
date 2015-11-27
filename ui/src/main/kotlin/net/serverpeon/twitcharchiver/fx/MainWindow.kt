package net.serverpeon.twitcharchiver.fx

import javafx.geometry.Insets
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import net.serverpeon.twitcharchiver.fx.sections.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.twitch.OAuthToken
import net.serverpeon.twitcharchiver.twitch.TwitchApi

class MainWindow(token: OAuthToken) : BorderPane() {
    private val api = ApiWrapper(TwitchApi(token))
    private val targetDirectory = TargetDirectoryInput()
    private val downloadControl = DownloadControl(api, targetDirectory.directoryProp)
    private val dataTable: VideoTable = VideoTable(downloadControl)
    private val downloadPane = DownloadPane(downloadControl, targetDirectory.directoryProp.isNotNull)

    private val oauthPane = OAuthInput(api, token)
    private val channelPane = ChannelInput(api, oauthPane.usernameProp, dataTable)

    init {
        targetDirectory.disableProperty().bind(downloadControl.isDownloadingProp) //Do not allow editing of path during a download

        center = ScrollPane(dataTable)
        bottom = downloadPane.node()
        right = vbox {
            +oauthPane

            +channelPane

            +targetDirectory

            forEach<Region> {
                padding = Insets(5.0, 5.0, 0.0, 5.0)
            }
        }
    }
}