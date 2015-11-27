package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.binding.When
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.Region
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.fx.vbox
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.twitch.api.KrakenApi
import net.serverpeon.twitcharchiver.twitch.playlist.Playlist
import rx.Observable
import rx.Subscription

class ChannelInput(val api: ApiWrapper, username: ReadOnlyStringProperty, val feed: ChannelInput.VideoFeed) : TitledPane() {
    companion object {
        private val DEBUG_USER: ReadOnlyStringProperty = SimpleStringProperty("lirik")
    }

    private val selectedUser = When(DEBUG_USER.isNotNull).then(DEBUG_USER).otherwise(username)

    private val videoLimit = SimpleIntegerProperty()
    private val loadingVideos = SimpleBooleanProperty(false)
    private var runningSubscription: Subscription? = null
        set(v: Subscription?) {
            field?.unsubscribe()
            field = v
            loadingVideos.set(field != null)
        }

    val hasAccess = api.hasAccess(this)

    init {
        text = "Channel"

        content = vbox {
            +hbox {
                +Label("Channel Name:").apply {
                    padding = Insets(0.0, 5.0, 0.0, 0.0)
                }
                +Label().apply {
                    textProperty().bind(selectedUser)
                    style = "-fx-font-weight: bold"
                    stretch(Orientation.HORIZONTAL)
                }

                init {
                    alignment = Pos.CENTER_RIGHT
                }
            }

            +hbox {
                +Label("Video Limit").apply {
                    padding = Insets(0.0, 5.0, 0.0, 0.0)
                }
                +Spinner(NonZeroSpinner(-1, Int.MAX_VALUE)).apply {
                    videoLimit.bind(valueProperty())
                    disableProperty().bind(loadingVideos)
                }

                init {
                    alignment = Pos.CENTER_RIGHT
                }
            }

            +vbox {
                +Button().apply {
                    stretch(Orientation.HORIZONTAL)
                    textProperty().bind(When(loadingVideos).then("Cancel Query").otherwise("Query Twitch for Videos"))

                    onActionProperty().bind(When(loadingVideos).then(EventHandler<ActionEvent> {
                        runningSubscription = null
                    }).otherwise(EventHandler {
                        feed.resetFeed()
                        runningSubscription = retrieveVideoStream(selectedUser.get(), videoLimit.get())
                                .doOnUnsubscribe { runningSubscription = null }
                                .subscribe {
                                    feed.insertVideo(it.first, it.second)
                                }
                    }))
                }

                +ProgressBar().apply {
                    progressProperty().bind(When(loadingVideos).then(-1.0).otherwise(0.0))
                    stretch(Orientation.HORIZONTAL)
                }
            }

            forEach<Region> {
                padding = Insets(3.0)
            }
        }

        disableProperty().bind(username.isNull.or(hasAccess.not()))
    }

    private fun retrieveVideoStream(username: String, limit: Int): Observable<Pair<KrakenApi.VideoListResponse.Video, Playlist>> {
        return api.request(this) {
            this.videoList(channelName = username, limit = limit)
                    .flatMap { video ->
                        this.loadPlaylist(video.internalId)
                                .map { Pair(video, it) }
                                .toObservable()
                    }
        }
    }

    private class NonZeroSpinner(min: Int, max: Int) : SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, min) {
        override fun increment(steps: Int) {
            super.increment(steps)
            if (value == 0) {
                super.increment(1)
            }
        }

        override fun decrement(steps: Int) {
            super.decrement(steps)
            if (value == 0) {
                super.decrement(1)
            }
        }
    }

    interface VideoFeed {
        fun resetFeed()

        fun insertVideo(info: KrakenApi.VideoListResponse.Video, playlist: Playlist)
    }
}