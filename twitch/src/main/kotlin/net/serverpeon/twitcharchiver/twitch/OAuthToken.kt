package net.serverpeon.twitcharchiver.twitch

import org.slf4j.LoggerFactory

/**
 * Wrapper object which protects the OAuth token during runtime.
 * It prevents accidental logging of the OAuth token.
 */
class OAuthToken(initialValue: String?) {
    var value: String? = initialValue
        set(v: String?) {
            if (v != field) {
                log.debug("OAuth token {}, value set to {}", System.identityHashCode(this), readValue(v))
                field = v
            }
        }

    init {
        log.debug("OAuth token {} created, initial value: {}", System.identityHashCode(this), readValue(value))
    }

    /**
     * Checks whether the token is currently set
     */
    fun hasValue(): Boolean {
        return value != null
    }

    val length: Int
        get() = value?.length ?: 0

    override fun equals(other: Any?): Boolean {
        return other is OAuthToken && other.value.equals(value)
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "OAuth{${readValue(value)}}"
    }

    private fun readValue(value: String?): String? {
        return value?.let { it.replaceRange(4, it.length - 4, "****") }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OAuthToken::class.java)
    }
}