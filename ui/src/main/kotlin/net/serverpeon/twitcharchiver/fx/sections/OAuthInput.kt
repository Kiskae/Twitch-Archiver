package net.serverpeon.twitcharchiver.fx.sections

import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.PasswordField
import javafx.scene.control.TitledPane
import net.serverpeon.twitcharchiver.fx.hbox
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.network.ApiWrapper
import net.serverpeon.twitcharchiver.twitch.OAuthToken

class OAuthInput(val api: ApiWrapper, val token: OAuthToken) : TitledPane() {
    private val processingValue = SimpleBooleanProperty(false)
    private val username = SimpleStringProperty(null)

    private val tokenInput = PasswordField().apply {
        text = token.value ?: ""

        focusedProperty().addListener { obs, old, new ->
            if (!old && new) {
                text = ""
            }
        }
    }
    private val submitButton = Button("Submit").apply {
        disableProperty().bind(tokenInput.textProperty().isEmpty)
        onAction = EventHandler {
            attemptAuth()
        }
    }

    val apiBinding = api.hasAccess(this)
    val usernameProp: ReadOnlyStringProperty
        get() = username

    init {
        text = "OAuth Token"

        content = hbox {
            +tokenInput.apply {
                stretch(Orientation.HORIZONTAL)
            }

            +submitButton
        }

        disableProperty().bind(apiBinding.not().or(processingValue))

        if (token.hasValue()) {
            attemptAuth()
        }
    }

    private fun attemptAuth() {
        processingValue.set(true)
        api.request(this) {
            username.set(null)
            token.value = scrubToken()
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

    private fun scrubToken(): String {
        val token = tokenInput.text
        if (token.startsWith("oauth:"))
            return token.substring("oauth:".length)
        else
            return token
    }
}