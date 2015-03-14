package net.serverpeon.twitcharchiver.twitch.impl;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.serverpeon.twitcharchiver.downloader.ProgressTracker;
import net.serverpeon.twitcharchiver.downloader.UriFileMapping;
import net.serverpeon.twitcharchiver.downloader.VideoSource;
import net.serverpeon.twitcharchiver.hls.HLSHandler;
import net.serverpeon.twitcharchiver.hls.HLSParser;
import net.serverpeon.twitcharchiver.hls.HLSPlaylist;
import net.serverpeon.twitcharchiver.twitch.OAuthToken;
import net.serverpeon.twitcharchiver.twitch.TwitchApi;
import net.serverpeon.twitcharchiver.twitch.UnrecognizedVodFormatException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinTask;

import static com.google.common.base.Preconditions.checkState;

public class HLSVideoSource implements VideoSource {
    private final static Logger logger = LogManager.getLogger(HLSVideoSource.class);

    private static final HLSHandler.KeyHandler TTV_TOTAL_SECONDS = new HLSHandler.KeyHandler() {
        @Override
        public void handle(String[] values, Map<String, Object> result) {
            final String[] timeSegments = values[0].split("\\.");
            result.put(
                    "EXT-X-TWITCH-TOTAL-SECS",
                    Long.parseLong(timeSegments[0]) * 1000 + Long.parseLong(timeSegments[1])
            );
        }
    };

    private final HLSPlaylist<HLSPlaylist.Video> playlist;
    private final int mutedCount;

    private HLSVideoSource(final HLSPlaylist<HLSPlaylist.Video> playlist, int mutedCount) {
        this.mutedCount = mutedCount;
        this.playlist = reduce(playlist);
    }

    private static HLSPlaylist<HLSPlaylist.Video> reduce(final HLSPlaylist<HLSPlaylist.Video> playList) {
        if (playList.resource.isEmpty()) return playList;

        try {
            final List<HLSPlaylist.Video> ret = Lists.newArrayList();
            final Iterator<HLSPlaylist.Video> it = playList.resource.iterator();

            HLSPlaylist.Video firstVideo = it.next();
            HLSPlaylist.Video lastVideo = firstVideo;
            long length = lastVideo.lengthMS;

            while (it.hasNext()) {
                final HLSPlaylist.Video next = it.next();

                if (!lastVideo.videoLocation.getPath().equals(next.videoLocation.getPath())) {
                    //Process lastVideo
                    final String startOffset =
                            firstVideo.videoLocation.getQuery().substring(0, firstVideo.videoLocation.getQuery().indexOf('&'));

                    final URI uri = UriBuilder.fromUri(lastVideo.videoLocation).replaceQuery(
                            lastVideo.videoLocation.getQuery().replaceAll("start_offset=[0-9]+", startOffset)
                    ).build();

                    ret.add(HLSPlaylist.Video.make(uri, length));
                    length = next.lengthMS;
                    firstVideo = next;
                } else {
                    length += next.lengthMS;
                }

                lastVideo = next;
            }
            final String startOffset =
                    firstVideo.videoLocation.getQuery().substring(0, firstVideo.videoLocation.getQuery().indexOf('&'));

            final URI uri = UriBuilder.fromUri(lastVideo.videoLocation).replaceQuery(
                    lastVideo.videoLocation.getQuery().replaceAll("start_offset=[0-9]+", startOffset)
            ).build();
            ret.add(HLSPlaylist.Video.make(uri, length));

            return new HLSPlaylist<>(ret, playList.properties);
        } catch (Exception ex) {
            //If a failure occurs, just skip the reduce step
            return playList;
        }
    }

    public static Optional<VideoSource> parse(final JsonElement element, final OAuthToken oauthToken) {
        try {
            final String apiId = element.getAsJsonObject().getAsJsonPrimitive("api_id").getAsString();
            checkState(apiId.startsWith("v"));

            final HLSPlaylist<HLSPlaylist.Source> sources = TwitchApi.getVodPlaylist(apiId.substring(1), oauthToken);
            final Optional<HLSPlaylist.Source> source = Iterables.tryFind(sources.resource, new Predicate<HLSPlaylist.Source>() {
                @Override
                public boolean apply(HLSPlaylist.Source source) {
                    return source.groupId.equals("chunked");
                }
            });

            if (source.isPresent()) {
                logger.debug("HLS playlist for {} is {}", apiId, source.get().playlistLocation);

                final HLSPlaylist<HLSPlaylist.Video> playlist = HLSParser.build(source.get().playlistLocation)
                        .addKeyHandler("EXT-X-TWITCH-TOTAL-SECS", TTV_TOTAL_SECONDS)
                        .parse();

                return Optional.<VideoSource>of(new HLSVideoSource(
                        playlist,
                        calculateMutedSegments(element.getAsJsonObject())
                ));
            } else {
                throw new UnrecognizedVodFormatException(new ParameterizedMessage(
                        "Unable to find chunked stream: {}",
                        sources
                ).getFormattedMessage());
            }
        } catch (Exception ex) {
            throw new UnrecognizedVodFormatException(ex);
        }
    }

    private static int calculateMutedSegments(JsonObject rootObject) {
        if (!rootObject.has("muted_segments")) return -1;

        final JsonArray array = rootObject.getAsJsonArray("muted_segments");

        int count = 0;
        for (JsonElement e : array) {
            final JsonPrimitive value = e.getAsJsonObject().getAsJsonPrimitive("duration");
            if (value.isNumber()) {
                //As of this commit, segments are 1 minute long.
                count += Math.ceil(value.getAsFloat() / 60.f);
            }
        }

        return count;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("playlist", playlist)
                .toString();
    }

    @Override
    public int getNumberOfParts() {
        return this.playlist.resource.size();
    }

    @Override
    public int getNumberOfMutedParts() {
        return mutedCount;
    }

    @Override
    public Runnable createDownloadTask(File targetFolder, ProgressTracker tracker) {
        return new HLSDownloader(targetFolder, tracker);
    }

    private class HLSDownloader implements Runnable {
        private final File targetFolder;
        private final ProgressTracker tracker;

        public HLSDownloader(File targetFolder, ProgressTracker tracker) {
            this.targetFolder = targetFolder;
            this.tracker = tracker;
        }

        @Override
        public void run() {
            if (playlist.properties.get(HLSHandler.EVENT_ENDED) != Boolean.TRUE) {
                return;
            }

            final UriFileMapping<HLSPlaylist.Video> fileMapping = new UriFileMapping<>(
                    playlist.resource,
                    new Function<HLSPlaylist.Video, URI>() {
                        @Override
                        public URI apply(HLSPlaylist.Video video) {
                            return video.videoLocation;
                        }
                    },
                    this.targetFolder
            );

            final List<ForkJoinTask<?>> downloaders = fileMapping.generateTasks(tracker, new Function<UriFileMapping<net.serverpeon.twitcharchiver.hls.HLSPlaylist.Video>.UriFileEntry, ForkJoinTask<?>>() {
                @Override
                public ForkJoinTask<?> apply(UriFileMapping<HLSPlaylist.Video>.UriFileEntry entry) {
                    return ForkJoinTask.adapt(new HLSPartDownloader(
                            entry.target,
                            entry.source,
                            tracker.track(entry.getURI().toString())
                    ));
                }
            });

            ForkJoinTask.invokeAll(downloaders);

            try (final PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(targetFolder, "ffmpeg-concat.txt")),
                    Charsets.UTF_8
            ))) {
                fileMapping.generateConcatFile(pw, targetFolder.toPath(), tracker);
            } catch (IOException e) {
                logger.warn("Failed to save ffmpeg concat file.", e);
            }
        }
    }

    private class HLSPartDownloader implements Runnable {
        private final File dest;
        private final HLSPlaylist.Video video;
        private final ProgressTracker.Partial tracker;

        public HLSPartDownloader(File targetFile, HLSPlaylist.Video video, ProgressTracker.Partial tracker) {
            this.dest = targetFile;
            this.video = video;
            this.tracker = tracker;
        }

        @Override
        public void run() {
            final byte buffer[] = new byte[1024 * 64]; //64kB

            try {
                //Make sure the target file exists
                dest.createNewFile();

                long totalRead = 0;
                logger.debug("Beginning download of {} to {}", video.videoLocation, dest);

                final URLConnection conn = video.videoLocation.toURL().openConnection();
                final long reportedSize = conn.getContentLengthLong();

                if (!MediaType.valueOf(conn.getContentType()).isCompatible(HLSParser.VIDEO_MPEG_TRANSPORT_STREAM)) {
                    throw new IOException(String.format(
                            "Twitch seems to be returning something that isn't a transport stream! (%s)",
                            conn.getContentType()
                    ));
                }

                try (final InputStream input = new BufferedInputStream(conn.getInputStream())) {
                    try (final OutputStream output = new FileOutputStream(dest)) {
                        int readBytes;
                        while ((readBytes = input.read(buffer)) > 0) {
                            output.write(buffer, 0, readBytes);
                            totalRead += readBytes;
                            tracker.update(totalRead, reportedSize);
                        }
                    }
                }

                if (totalRead < (video.lengthMS / 1000)) {
                    //Heuristic, if the video is less than 1bps, its probably corrupted/wrong
                    throw new IOException(String.format(
                            "Invalid file size %d bytes, %d seconds",
                            totalRead,
                            (video.lengthMS / 1000)
                    ));
                }

                tracker.finish();

                logger.debug("{} downloaded!", dest);
            } catch (IOException e) {
                logger.warn(new ParameterizedMessage("Error downloading {} to {}", video.videoLocation, dest), e);
                dest.delete();
                tracker.invalidate();
            }
        }
    }
}
