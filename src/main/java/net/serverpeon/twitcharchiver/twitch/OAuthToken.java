package net.serverpeon.twitcharchiver.twitch;

import com.google.common.base.MoreObjects;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;

public class OAuthToken {
    private final String token;

    public OAuthToken(final String token) {
        this.token = token;
    }

    public WebTarget queryParam(final WebTarget target, final String name) {
        return target.queryParam(name, token);
    }

    public Invocation.Builder header(Invocation.Builder target) {
        return target.header("Authorization", "Bearer " + token);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                //For privacy, ensure the token is never fully logged
                .add("token", token.substring(token.length() / 2))
                .toString();
    }
}
