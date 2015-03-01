package net.serverpeon.twitcharchiver.hls;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HLSParser {
    public final static MediaType APPLICATION_M3U = new MediaType("application", "x-mpegURL");
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

    public HLSPlaylist parse() {
        final Response response = client.target(playlistLocation)
                .request()
                .accept(APPLICATION_M3U)
                .get();

        if (response.getStatus() != 200) {
            throw new MalformedPlaylistException("Response was not HTTP 200");
        }

        final InputStream stream = response.readEntity(InputStream.class);
        return process(new Scanner(stream, "UTF-8"));
    }

    private HLSPlaylist process(final Scanner scanner) {
        final Map<String, Object> state = Maps.newHashMap();
        if (!(scanner.hasNextLine() || scanner.nextLine().equals("#EXTM3U"))) {
            throw new MalformedPlaylistException("Missing #EXTM3U header");
        }

        final List<HLSPlaylist.Video> videos = Lists.newArrayList();

        while (scanner.hasNextLine()) {
            final String line = scanner.nextLine().trim();

            //Ignore empty lines
            if (line.isEmpty()) continue;

            if (line.startsWith("#")) {
                //HLS handler
                handler.handle(line, state);
            } else {
                final URI videoLocation = playlistLocation.resolve(line);
                final Number length = ((Number) state.remove(HLSHandler.EXTINF_KEY));
                videos.add(HLSPlaylist.Video.make(
                        videoLocation,
                        length != null ? length.longValue() : -1
                ));
            }
        }

        return new HLSPlaylist(videos, state);
    }
}
