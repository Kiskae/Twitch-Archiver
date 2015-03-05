package net.serverpeon.twitcharchiver.hls;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.message.ParameterizedMessage;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HLSParser {
    public final static MediaType APPLICATION_M3U = new MediaType("application", "x-mpegURL");
    public final static MediaType APPLICATION_HLS_PLAYLIST = new MediaType("application", "vnd.apple.mpegurl");
    public final static MediaType VIDEO_MPEG_TRANSPORT_STREAM = new MediaType("video", "MP2T");

    private final HLSHandler handler = new HLSHandler();
    private final Client client = ClientBuilder.newClient();
    private final URI playlistLocation;

    private HLSParser(final URI playlist) {
        playlistLocation = playlist;
    }

    public static HLSParser build(URI playListLocation) {
        return new HLSParser(playListLocation);
    }

    public HLSParser addKeyHandler(final String key, final HLSHandler.KeyHandler handler) {
        this.handler.addHandler(key, handler);
        return this;
    }

    public HLSPlaylist<HLSPlaylist.Video> parse() {
        return retrieveAndParsePlaylist(APPLICATION_M3U, new HLSTypeProcessor<HLSPlaylist.Video>() {
            @Override
            public HLSPlaylist.Video process(URI location, Map<String, Object> state) {
                final Number length = ((Number) state.remove(HLSHandler.EXTINF_KEY));
                return HLSPlaylist.Video.make(
                        location,
                        length != null ? length.longValue() : -1
                );
            }
        });
    }

    public HLSPlaylist<HLSPlaylist.Source> parseSourceList() {
        return retrieveAndParsePlaylist(APPLICATION_HLS_PLAYLIST, new HLSTypeProcessor<HLSPlaylist.Source>() {
            @Override
            public HLSPlaylist.Source process(URI location, Map<String, Object> state) {
                final String videoType = (String) Objects.firstNonNull(
                        state.remove(HLSHandler.PLAYLIST_MEDIA + "-GROUP-ID"),
                        state.remove(HLSHandler.PLAYLIST_STREAM_INF + "-VIDEO"));
                final long bandwidth = (long) state.remove(HLSHandler.PLAYLIST_STREAM_INF + "-BANDWIDTH");
                final String[] codecs = (String[]) state.remove(HLSHandler.PLAYLIST_STREAM_INF + "-CODECS");

                //Clean up playlist-specific values.
                final Iterator<Map.Entry<String, Object>> it = state.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<String, Object> entry = it.next();
                    if (entry.getKey().startsWith(HLSHandler.PLAYLIST_MEDIA)
                            || entry.getKey().startsWith(HLSHandler.PLAYLIST_STREAM_INF)) {
                        it.remove();
                    }
                }

                return HLSPlaylist.Source.make(videoType, bandwidth, codecs, location);
            }
        });
    }

    private <E> HLSPlaylist<E> retrieveAndParsePlaylist(
            final MediaType expectedType,
            final HLSTypeProcessor<E> processor
    ) {
        final Response response = client.target(playlistLocation)
                .request()
                .accept(expectedType)
                .get();

        if (response.getStatus() != 200) {
            throw new MalformedPlaylistException(
                    new ParameterizedMessage("Response was not HTTP 200: {}", response).getFormattedMessage()
            );
        }

        final InputStream stream = response.readEntity(InputStream.class);
        return process(new Scanner(stream, "UTF-8"), processor);
    }

    private <E> HLSPlaylist<E> process(final Scanner scanner, final HLSTypeProcessor<E> processor) {
        final Map<String, Object> state = Maps.newHashMap();

        do {
            if (scanner.hasNextLine()) {
                final String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    //If the first non-empty line isn't the header, its invalid.
                    if (line.equals("#EXTM3U")) {
                        break;
                    } else {
                        throw new MalformedPlaylistException("Missing #EXTM3U header");
                    }
                }
            } else {
                throw new MalformedPlaylistException("Empty file");
            }
        } while (true);

        final List<E> videos = Lists.newArrayList();

        while (scanner.hasNextLine()) {
            final String line = scanner.nextLine().trim();

            //Ignore empty lines
            if (line.isEmpty()) continue;

            if (line.startsWith("#")) {
                //HLS handler
                handler.handle(line, state);
            } else {
                final URI videoLocation = playlistLocation.resolve(line);
                videos.add(processor.process(videoLocation, state));
            }
        }

        return new HLSPlaylist<>(videos, state);
    }

    private interface HLSTypeProcessor<E> {
        E process(URI location, Map<String, Object> state);
    }
}
