package net.serverpeon.twitcharchiver.twitch

/**
 * Wrapper object which protects the OAuth token during runtime.
 * It prevents accidental logging of the OAuth token.
 */
class OAuthToken(internal var internalToken: String?) {

    /**
     * Change the internal token, any future requests to [TwitchApi] provided with this token
     * will use the revised token.
     */
    fun setToken(token: String) {
        internalToken = token
    }

    override fun equals(other: Any?): Boolean {
        return other is OAuthToken && other.internalToken.equals(internalToken)
    }

    override fun hashCode(): Int {
        return internalToken?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "OAuth{${internalToken?.replaceRange(4, internalToken!!.length, "*")}}"
    }
}