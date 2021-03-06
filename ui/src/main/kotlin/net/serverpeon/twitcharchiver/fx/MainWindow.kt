package net.serverpeon.twitcharchiver.fx

import javafx.geometry.Insets
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import net.serverpeon.twitcharchiver.fx.sections.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.twitch.OAuthToken
import net.serverpeon.twitcharchiver.twitch.LegacyTwitchApi

class MainWindow(token: OAuthToken) : BorderPane() {
    private val api = ApiWrapper(LegacyTwitchApi(token))
    private val targetDirectory = TargetDirectoryInput()
    private val parallelismPanel = ParallelismPane()
    private val downloadControl = DownloadControl(api, targetDirectory.directoryProp, parallelismPanel.parallelismProp) {
        dataTable.selectedVideos() //Retrieve the selected videos
    }

    private val dataTable: VideoTable = VideoTable(downloadControl)
    private val downloadPane = DownloadPane(downloadControl, targetDirectory.directoryProp.isNotNull)

    private val oauthPane = OAuthInput(api, token)
    private val channelPane = ChannelInput(api, oauthPane.usernameProp, dataTable)

    init {
        //Do not allow editing of path or parallelism during a download
        targetDirectory.disableProperty().bind(downloadControl.isDownloadingProp)
        parallelismPanel.disableProperty().bind(downloadControl.isDownloadingProp)

        center = dataTable
        bottom = downloadPane.node()
        right = vbox {
            +oauthPane

            +channelPane

            +targetDirectory

            +parallelismPanel

            forEach<Region> {
                padding = Insets(5.0, 5.0, 0.0, 5.0)
            }
        }
    }
}