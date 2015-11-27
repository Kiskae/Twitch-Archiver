package net.serverpeon.twitcharchiver.fx

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import net.serverpeon.twitcharchiver.fx.sections.*
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.network.DownloadableVod
import net.serverpeon.twitcharchiver.twitch.OAuthToken
import net.serverpeon.twitcharchiver.twitch.TwitchApi
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist

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
        right = createControlPane(token, infoTable)
    }

    private fun createControlPane(token: OAuthToken, table: VideoTable): Node {
        val sectionPadding = Insets(5.0, 5.0, 0.0, 5.0)
        return vbox {
            val oauth = OAuthInput(api, token)
            +oauth

            oauth.usernameProp.addListener { observableValue, old, new ->
                println("$old -> $new")
            }

            +ChannelInput(api, oauth.usernameProp, object : ChannelInput.VideoFeed {
                override fun insertVideo(info: KrakenApi.VideoListResponse.Video, playlist: Playlist) {
                    table.videos.add(DownloadableVod(info, playlist))
                }

                override fun resetFeed() {
                    table.videos.clear()
                }
            })

            +TargetDirectoryInput()

            forEach<Region> {
                padding = sectionPadding
            }
        }
    }
}