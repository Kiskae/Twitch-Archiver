package net.serverpeon.twitcharchiver.twitch

/**
 * Wrapper object which protects the OAuth token during runtime.
 * It prevents accidental logging of the OAuth token.
 */
class OAuthToken(var value: String?) {
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
        return "OAuth{${value?.replaceRange(4, value!!.length, "*")}}"
    }
}