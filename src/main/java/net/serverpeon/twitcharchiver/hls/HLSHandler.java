package net.serverpeon.twitcharchiver.hls;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

public class HLSHandler {
    public final static String EXTINF_KEY = "EXTINF";
    public final static String EVENT_ENDED = "EXT-X-ENDLIST";
    static {
        DEFAULT_HANDLERS = ImmutableMap
                .<String, KeyHandler>builder()
                .put("EXT-X-VERSION", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        //TODO: check spec
                    }
                }).put("EXT-X-TARGETDURATION", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        //TODO: check spec
                    }
                }).put("EXT-X-PLAYLIST-TYPE", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        //TODO: check spec
                    }
                }).put("EXTINF", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        final String[] timeSegments = values[0].split("\\.");
                        result.put(
                                EXTINF_KEY,
                                Long.parseLong(timeSegments[0]) * 1000 + Long.parseLong(timeSegments[1])
                        );
                    }
                }).put("EXT-X-ENDLIST", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        result.put(EVENT_ENDED, Boolean.TRUE);
                    }
                }).build();
    }
    private static final Map<String, KeyHandler> DEFAULT_HANDLERS;
    private final Map<String, KeyHandler> handlers;

    public HLSHandler() {
        handlers = Maps.newHashMap(DEFAULT_HANDLERS);
    }

    public void addHandler(final String key, final KeyHandler handler) {
        if (handlers.containsKey(key)) {
            throw new IllegalStateException();
        } else {
            handlers.put(key, handler);
        }
    }

    public void handle(final String line, final Map<String, Object> result) {
        //remove prefix and split by ':'
        final String[] parts = line.substring(1).split(":", 2);
        final KeyHandler handler = handlers.get(parts[0]);
        if (handler != null) {

            handler.handle(
                    parts.length == 2 ? parts[1].split(",") : new String[0],
                    result
            );
        }
    }

    public static interface KeyHandler {
        void handle(final String[] values, final Map<String, Object> result);
    }
}
