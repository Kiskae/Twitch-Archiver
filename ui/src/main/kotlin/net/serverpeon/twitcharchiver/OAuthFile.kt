package net.serverpeon.twitcharchiver

import com.google.common.io.CharSink
import com.google.common.io.CharSource
import net.serverpeon.twitcharchiver.twitch.TwitchApi
import java.security.MessageDigest
import java.util.*

data class OAuthFile private constructor(private val oauth: String?, private val invalid: Exception?) {
    fun write(sink: CharSink) {
        val key = oauthKey()

        sink.openBufferedStream().use {
            it.appendln(version.toString())
            it.appendln(key)
            it.appendln(generateHash(TwitchApi.TWITCH_CLIENT_ID, key))
        }
    }

    fun oauthKey(): String {
        check(isValid())
        return oauth!!
    }

    fun isValid(): Boolean {
        return invalid == null
    }

    fun invalidationReason(): Exception {
        return invalid!!
    }

    companion object {
        private val version: Int = 1

        fun from(oauth: String): OAuthFile {
            return OAuthFile(oauth, null)
        }

        fun read(source: CharSource): OAuthFile {
            return source.openBufferedStream().use {
                val creationVersion = it.readLine().toInt()
                val key = it.readLine()
                val hash = it.readLine()

                if (creationVersion != version) {
                    OAuthFile(null, IllegalStateException("Incompatible file: version difference"))
                } else if (hash != generateHash(TwitchApi.TWITCH_CLIENT_ID, key)) {
                    OAuthFile(null, IllegalStateException("Incompatible file: hash mismatch"))
                } else {
                    OAuthFile(key, null)
                }
            }
        }

        private fun generateHash(clientId: String, oauthKey: String): String {
            val crypt = MessageDigest.getInstance("SHA-1")
            crypt.reset()
            crypt.update(clientId.toByteArray())
            crypt.update(oauthKey.toByteArray())
            crypt.digest()
            return Base64.getEncoder().encodeToString(crypt.digest())
        }
    }
}