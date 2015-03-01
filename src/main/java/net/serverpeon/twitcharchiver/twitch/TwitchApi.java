package net.serverpeon.twitcharchiver.twitch;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.serverpeon.twitcharchiver.twitch.impl.HLSVideoSource;
import net.serverpeon.twitcharchiver.twitch.impl.LegacyVideoSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This class defines all interactions and constants for the required interactions with the Twitch API.
 * <br>
 * The following API endpoints are used:
 * GET / - To validate the user-provided OAuth token
 * GET /channels/:channel/videos - To retrieve a list of all past broadcasts.
 * <br>
 * GET api.justin.tv/api/broadcast/by_archive/:broadcast_id.json - To retrieve the background-information of the VOD
 * * This one is probably deprecated and not well documented.
 *
 * @author Kiskae
 * @see <a href="https://github.com/justintv/Twitch-API">Twitch API documentation</a>
 * @since 2014-08-10
 */
public class TwitchApi {
    private final static Logger logger = LogManager.getLogger(TwitchApi.class);
    private final static MediaType APPLICATION_TWITCH_JSON = new MediaType("application", "vnd.twitchtv.v3+json");
    private final static String TWITCH_OAUTH_URL_PARAM = "oauth_token";
    private final static WebTarget TWITCH_API_KRAKEN;
    private final static WebTarget TWITCH_API_INTERNAL;
    private final static int PAST_BROADCASTS_MAX_LIMIT = 100;

    static {
        final Client client = ClientBuilder.newClient();
        TWITCH_API_KRAKEN = client.target("https://api.twitch.tv").path("kraken");
        TWITCH_API_INTERNAL = client.target("https://api.twitch.tv").path("api");
    }

    /**
     * Validates the given OAuth token with Twitch.TV
     *
     * @param oauthToken OAuth token to validate
     * @return The user name associated with the given token
     * @throws InvalidOAuthTokenException                              If the given token is not valid
     * @throws net.serverpeon.twitcharchiver.twitch.TwitchApiException if the twitch api returned unexpected results
     */
    public static String getTwitchUsernameWithOAuth(final String oauthToken) throws InvalidOAuthTokenException {
        Preconditions.checkNotNull(oauthToken, "OAuth token cannot be NULL");

        final Response response = TWITCH_API_KRAKEN
                .queryParam(TWITCH_OAUTH_URL_PARAM, oauthToken)
                .request(APPLICATION_TWITCH_JSON)
                .get();

        //Truncate oauth token
        logger.debug("getTwitchUsernameWithOAuth: {}", oauthToken.substring(oauthToken.length() / 2));
        if (response.getStatus() != 200) {
            throw new TwitchApiException(response);
        } else {
            final JsonObject jsonResponse = new JsonParser().parse(response.readEntity(String.class)).getAsJsonObject();
            final JsonObject tokenObj = jsonResponse.getAsJsonObject("token");
            if (tokenObj.get("valid").getAsBoolean()) {
                //OAuth token is valid
                return tokenObj.get("user_name").getAsString();
            } else {
                //Not a valid oauth token!
                throw new InvalidOAuthTokenException(oauthToken);
            }
        }
    }

    private static Optional<JsonObject> getChannelVideos(
            final WebTarget target,
            final int limit,
            final int offset,
            final JsonParser parser
    ) {
        final Response response = target
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .request(APPLICATION_TWITCH_JSON)
                .get();

        if (response.getStatus() == 404 || response.getStatus() == 422) {
            //404 = channel does not exist
            //422 = channel has been banned
            //Both cases mean no videos
            return Optional.absent();
        } else if (response.getStatus() != 200) {
            throw new TwitchApiException(response);
        } else {
            return Optional.of(parser.parse(response.readEntity(String.class)).getAsJsonObject());
        }
    }

    /**
     * Retrieve a list of all past broadcasts for the given channel
     *
     * @param channelName name of the channel for which to retrieve past broadcasts.
     * @return a list of data describing all past broadcasts on the channel.
     * @throws net.serverpeon.twitcharchiver.twitch.TwitchApiException if the twitch api returned unexpected results
     */
    public static Iterator<JsonElement> getAllPastBroadcastsForChannel(final String channelName, final String oAuthToken) {
        Preconditions.checkNotNull(channelName, "Channel name cannot be NULL");

        final WebTarget channelTarget = TWITCH_API_KRAKEN
                .path("channels")
                .path(channelName)
                .path("videos")
                .queryParam("broadcasts", true)
                .queryParam(TWITCH_OAUTH_URL_PARAM, oAuthToken);

        final JsonParser parser = new JsonParser();

        logger.debug("getAllPastBroadcastsForChannel: {}", channelName);
        final Optional<JsonObject> response = getChannelVideos(channelTarget, PAST_BROADCASTS_MAX_LIMIT, 0, parser);

        if (response.isPresent()) {
            final JsonObject firstResponse = response.get();

            return new Iterator<JsonElement>() {
                final int totalVideos = firstResponse.get("_total").getAsInt();
                Iterator<JsonElement> videos = firstResponse.getAsJsonArray("videos").iterator();
                int nextOffset = 100;

                @Override
                public boolean hasNext() {
                    return videos.hasNext() || nextOffset < totalVideos;
                }

                @Override
                public JsonElement next() {
                    if (!videos.hasNext() && nextOffset < totalVideos) {
                        final JsonObject responseObj = getChannelVideos(
                                channelTarget,
                                Math.min(PAST_BROADCASTS_MAX_LIMIT, totalVideos - nextOffset),
                                nextOffset,
                                parser
                        ).get();

                        videos = responseObj.getAsJsonArray("videos").iterator();
                        nextOffset += 100;
                    }

                    if (videos.hasNext()) {
                        return videos.next();
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        } else {
            //No videos available for this channel
            return Iterators.emptyIterator();
        }
    }

    /**
     * @param channelName
     * @param limit
     * @return
     */
    public static Iterator<JsonElement> getLimitedPastBroadcastsForChannel(final String channelName, final String oAuthToken, final int limit) {
        Preconditions.checkNotNull(channelName, "Channel name cannot be NULL");
        Preconditions.checkArgument(limit > 0, "Limit needs to be larger than 0");

        if (limit < PAST_BROADCASTS_MAX_LIMIT) {
            final WebTarget channelTarget = TWITCH_API_KRAKEN
                    .path("channels")
                    .path(channelName)
                    .path("videos")
                    .queryParam("broadcasts", true)
                    .queryParam(TWITCH_OAUTH_URL_PARAM, oAuthToken);

            final JsonParser parser = new JsonParser();

            logger.debug("getLimitedPastBroadcastsForChannel: {}, {}", channelName, limit);
            final Optional<JsonObject> response = getChannelVideos(channelTarget, limit, 0, parser);
            if (response.isPresent()) {
                return response.get().getAsJsonArray("videos").iterator();
            } else {
                return Iterators.emptyIterator();
            }
        } else {
            //If larger than
            return Iterators.limit(getAllPastBroadcastsForChannel(channelName, oAuthToken), limit);
        }
    }

    /**
     * @param broadcastData
     * @return
     */
    public static BroadcastInformation getBroadcastInformation(final JsonElement broadcastData, final String oAuthToken) {
        Preconditions.checkNotNull(broadcastData, "Data cannot be NULL");

        final JsonObject obj = broadcastData.getAsJsonObject();
        final String title = obj.get("title").getAsString();
        final int views = obj.get("views").getAsInt();
        final int length = obj.get("length").getAsInt(); //In Seconds
        final String broadcastId = obj.get("_id").getAsString();
        final DateTime recorded_at = DateTime.parse(obj.get("recorded_at").getAsString());

        final Optional<VideoSource> source = getVideoSource(broadcastId, oAuthToken);

        if (!source.isPresent()) throw new SubscriberOnlyException();
        return new BroadcastInformation(title, views, length, broadcastId, source.get(), recorded_at);
    }

    private static Optional<VideoSource> getVideoSource(final String broadcastId, final String oAuthToken) {
        final Response response = TWITCH_API_INTERNAL
                .path("videos")
                .path(broadcastId)
                .queryParam(TWITCH_OAUTH_URL_PARAM, oAuthToken)
                .request(APPLICATION_TWITCH_JSON)
                .get();

        logger.debug("getInternalVideoInformation: {}", broadcastId);
        if (response.getStatus() != 200) {
            throw new TwitchApiException(response);
        } else {
            final JsonElement result = new JsonParser().parse(response.readEntity(String.class));
            if (broadcastId.startsWith("b")) {
                //Old style video storage
                return LegacyVideoSource.parse(result);
            } else if (broadcastId.startsWith("v")) {
                //HLS style video storage
                return HLSVideoSource.parse(result);
            } else {
                //Unrecognized video storage
                throw new UnrecognizedVodFormatException(response.getLocation().toString());
            }
        }
    }
}
