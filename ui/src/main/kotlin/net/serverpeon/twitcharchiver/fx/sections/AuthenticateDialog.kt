package net.serverpeon.twitcharchiver.fx.sections

import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.scene.control.*
import net.serverpeon.twitcharchiver.fx.stretch
import net.serverpeon.twitcharchiver.fx.vbox
import net.serverpeon.twitcharchiver.twitch.OAuthHelper
import okhttp3.HttpUrl
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class AuthenticateDialog(val state: String) : Dialog<String>() {
    val responseInput = TextField()

    init {
        val authUrl = OAuthHelper.authenticationUrl(REDIRECT_URI, listOf(), state)

        title = "Twitch Authorization"
        dialogPane.content = vbox {
            +Label(REASON_TEXT)

            +Hyperlink(authUrl.toString()).apply {
                onAction = EventHandler {
                    directUserToURL(authUrl)
                }
                prefWidth = 400.0
            }

            +Label(FOLLOWUP_TEXT)

            +responseInput.apply {
                stretch(Orientation.HORIZONTAL)
            }
        }

        dialogPane.buttonTypes.add(ButtonType.OK)
        dialogPane.buttonTypes.add(ButtonType.CLOSE)

        setResultConverter { type ->
            when (type) {
                ButtonType.OK -> {
                    responseInput.text.let {
                        if (it.isEmpty()) null
                        else {
                            try {
                                OAuthHelper.extractAccessToken(responseInput.text, state)
                            } catch (e: Exception) {
                                log.debug("Failed to extract access token", e)
                                null
                            }
                        }
                    }
                }
                else -> null
            }
        }
    }

    companion object {
        private val REDIRECT_URI = HttpUrl.parse("http://localhost:4/") ?: error("Parsing error")

        private val REASON_TEXT = """
        In order to download videos from Twitch on your behalf we need you to authorize us.
        Click the link below and a browser should open with a prompt from Twitch to authorize
        this application.
        """.trimIndent()

        private val FOLLOWUP_TEXT = """
        Once you have authorized access you will be redirected to an empty page or a page showing an error.
        Copy the current URL from the browser into the textfield below and click "OK".
        """.trimIndent()

        private val log = LoggerFactory.getLogger(AuthenticateDialog::class.java)

        private fun directUserToURL(url: HttpUrl) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                // Open the url on the browser
                Desktop.getDesktop().browse(url.uri())
            } else {
                // As a fallback, copy to clipboard and alert user
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(url.toString()), null)
                Alert(Alert.AlertType.INFORMATION).apply {
                    contentText = "Unable to open browser, the URL has been copied onto your clipboard."
                }.show()
            }
        }
    }
}