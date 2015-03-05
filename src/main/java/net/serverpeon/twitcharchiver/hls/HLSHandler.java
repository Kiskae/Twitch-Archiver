package net.serverpeon.twitcharchiver.hls;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HLSHandler {
    private final static Logger logger = LogManager.getLogger(HLSHandler.class);

    public final static String EXTINF_KEY = "EXTINF";
    public final static String EVENT_ENDED = "EXT-X-ENDLIST";
    public final static String PLAYLIST_MEDIA = "EXT-X-MEDIA";
    public final static String PLAYLIST_STREAM_INF = "EXT-X-STREAM-INF";
    public final static String VERSION_KEY = "EXT-X-VERSION";
    public final static String SEGMENT_TARGET_DURATION = "EXT-X-TARGETDURATION";
    public final static String PLAYLIST_TYPE = "EXT-X-PLAYLIST-TYPE";

    static {
        DEFAULT_HANDLERS = ImmutableMap
                .<String, KeyHandler>builder()
                .put("EXT-X-VERSION", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        result.put(VERSION_KEY, Integer.parseInt(values[0]));
                    }
                }).put("EXT-X-TARGETDURATION", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        result.put(SEGMENT_TARGET_DURATION, Integer.parseInt(values[0]));
                    }
                }).put("EXT-X-PLAYLIST-TYPE", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        result.put(PLAYLIST_TYPE, values[0]);
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
                }).put("EXT-X-MEDIA", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        for (String keyValue : values) {
                            final String[] split = keyValue.split("=", 2);
                            switch (split[0]) {
                                case "URI":
                                    result.put(PLAYLIST_MEDIA + "-URI", URI.create(unquote(split[1], '"')));
                                    break;
                                case "LANGUAGE":
                                    result.put(PLAYLIST_MEDIA + "-LANGUAGE", Locale.forLanguageTag(
                                            unquote(split[1], '"')
                                    ));
                                    break;
                                case "DEFAULT":
                                    result.put(PLAYLIST_MEDIA + "-DEFAULT", split[1].equals("YES"));
                                    break;
                                case "AUTOSELECT":
                                    result.put(PLAYLIST_MEDIA + "-AUTOSELECT", split[1].equals("YES"));
                                    break;
                                default:
                                    result.put(
                                            PLAYLIST_MEDIA + "-" + split[0],
                                            split.length == 2 ? unquote(split[1], '"') : null
                                    );
                                    break;
                            }
                        }
                    }
                }).put("EXT-X-STREAM-INF", new KeyHandler() {
                    @Override
                    public void handle(String[] values, Map<String, Object> result) {
                        for (String keyValue : values) {
                            final String[] split = keyValue.split("=", 2);
                            switch (split[0]) {
                                case "BANDWIDTH":
                                    //bps
                                    result.put(PLAYLIST_STREAM_INF + "-BANDWIDTH", Long.parseLong(split[1]));
                                    break;
                                case "PROGRAM-ID":
                                    result.put(PLAYLIST_STREAM_INF + "-PROGRAM-ID", Integer.parseInt(split[1]));
                                    break;
                                case "CODECS":
                                    result.put(PLAYLIST_STREAM_INF + "-CODECS", unquote(split[1], '"').split(","));
                                    break;
                                default:
                                    result.put(
                                            PLAYLIST_STREAM_INF + "-" + split[0],
                                            split.length == 2 ? unquote(split[1], '"') : null
                                    );
                                    break;
                            }
                        }
                    }
                }).put("EXT-X-ENDLIST", new KeyHandler() {
                            @Override
                            public void handle(String[] values, Map<String, Object> result) {
                                result.put(EVENT_ENDED, Boolean.TRUE);
                            }
                        }
                ).build();
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
                    parts.length == 2 ? splitUnquoted(parts[1]) : new String[0],
                    result
            );
        }
    }

    private static String[] splitUnquoted(String str) {
        final List<String> valueStack = Lists.newLinkedList();
        int start = 0;
        int end = start;

        boolean skip = false;
        boolean inQuotes = false;
        for (char c : str.toCharArray()) {
            if (skip) {
                skip = false;
            } else {
                switch (c) {
                    case '\\':
                        skip = true;
                        break;
                    case '"':
                        inQuotes = !inQuotes;
                        break;
                    case ',':
                        if (!inQuotes) {
                            valueStack.add(str.substring(start, end));
                            start = end + 1;
                        }
                        break;
                }
            }
            ++end;
        }

        if (start != end) {
            valueStack.add(str.substring(start, end));
        }

        return valueStack.toArray(new String[valueStack.size()]);
    }

    private static String unquote(String str, char quoteChar) {
        final int len = str.length();
        if (str.charAt(0) == quoteChar && str.charAt(len - 1) == quoteChar) {
            return str.substring(1, len - 1);
        } else {
            return str;
        }
    }

    public static interface KeyHandler {
        void handle(final String[] values, final Map<String, Object> result);
    }
}
