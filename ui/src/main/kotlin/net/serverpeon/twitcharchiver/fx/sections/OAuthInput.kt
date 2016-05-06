package net.serverpeon.twitcharchiver.fx.sections

import com.google.common.io.Files
import javafx.beans.binding.When
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TitledPane
import javafx.stage.FileChooser
import javafx.stage.Window
import net.serverpeon.twitcharchiver.OAuthFile
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.twitch.OAuthToken
import java.io.File

class OAuthInput(val api: ApiWrapper, val token: OAuthToken) : TitledPane() {
    private val processingValue = SimpleBooleanProperty(false)
    private val username = SimpleStringProperty(null)

    private val authorizeButton = Button("Authorize").apply {
        onAction = EventHandler {
            val result = AuthenticateDialog("badgers.").showAndWait()
            result.ifPresent { attemptAuth(it) }
        }
    }

    private val importButton = Button("Import").apply {
        onAction = EventHandler {
            val key = importOauthKey(it.window())
            if (key != null) {
                attemptAuth(key)
            }
        }
    }

    private val exportButton = Button("Export").apply {
        disableProperty().bind(username.isEmpty)
        onAction = EventHandler {
            exportOauthKey(
                    token.value!!,
                    username.get(),
                    it.window()
            )
        }
    }

    val apiBinding = api.hasAccess(this)
    val usernameProp: ReadOnlyStringProperty
        get() = username

    init {
        text = "OAuth Token"

        content = hbox {
            +Label("Current status: ")
            +Label().apply {
                style = "-fx-font-weight: bold"
                textProperty().bind(When(username.isEmpty).then("Unauthorized").otherwise("Authorized"))
            }

            +Separator(Orientation.VERTICAL)

            +authorizeButton

            +importButton

            +exportButton

            init {
                alignment = Pos.CENTER_RIGHT
            }
        }

        disableProperty().bind(apiBinding.not().or(processingValue))
    }

    private fun attemptAuth(oauthKey: String) {
        processingValue.set(true)
        api.request(this) {
            username.set(null)
            token.value = oauthKey
            this.retrieveUser().toObservable()
        }.doOnTerminate {
            processingValue.set(false)
        }.subscribe {
            if (it.isPresent) {
                username.set(it.get())
            } else {
                println("Invalid token")
                //TODO: dialog, invalid token
            }
        }
    }

    companion object {
        private val OAUTH_EXTENSION_FILTER = FileChooser.ExtensionFilter("OAuth key files", "*.oauth")
        private val TWITCH_ARCHIVER_DIRECTORY = File(System.getProperty("user.home"), "twitch-archiver").apply {
            mkdirs()
        }

        private fun Event.window(): Window {
            return (this.target as Node).scene.window
        }

        private fun exportOauthKey(key: String, suggestedName: String, window: Window) {
            val outputFile = FileChooser().apply {
                this.extensionFilters.clear()
                this.extensionFilters.add(OAUTH_EXTENSION_FILTER)
                this.initialFileName = suggestedName
                this.initialDirectory = TWITCH_ARCHIVER_DIRECTORY
            }.showSaveDialog(window)

            if (outputFile != null) {
                OAuthFile.from(key).write(Files.asCharSink(outputFile, Charsets.UTF_8))
            }
        }

        private fun importOauthKey(window: Window): String? {
            val inputFile = FileChooser().apply {
                this.extensionFilters.clear()
                this.extensionFilters.add(OAUTH_EXTENSION_FILTER)
                this.initialDirectory = TWITCH_ARCHIVER_DIRECTORY
            }.showOpenDialog(window)

            return if (inputFile != null) {
                val keyFile = OAuthFile.read(Files.asCharSource(inputFile, Charsets.UTF_8))
                if (keyFile.isValid()) {
                    keyFile.oauthKey()
                } else {
                    keyFile.invalidationReason().printStackTrace()
                    null
                }
            } else {
                null
            }
        }
    }
}