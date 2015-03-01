package net.serverpeon.twitcharchiver.twitch.impl;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.serverpeon.twitcharchiver.downloader.ForkJoinDownloadTask;
import net.serverpeon.twitcharchiver.downloader.ProgressTracker;
import net.serverpeon.twitcharchiver.hls.HLSHandler;
import net.serverpeon.twitcharchiver.hls.HLSParser;
import net.serverpeon.twitcharchiver.hls.HLSPlaylist;
import net.serverpeon.twitcharchiver.twitch.UnrecognizedVodFormatException;
import net.serverpeon.twitcharchiver.twitch.VideoSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinTask;

public class HLSVideoSource implements VideoSource {
    private final static Logger logger = LogManager.getLogger(HLSVideoSource.class);

    private static final URI TTV_VOD_CDN = URI.create("http://vod.ak.hls.ttvnw.net/");
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

    private final HLSPlaylist playlist;
    private final int mutedCount;

    private HLSVideoSource(final HLSPlaylist playlist, int mutedCount) {
        this.mutedCount = mutedCount;
        this.playlist = reduce(playlist);
    }

    private static HLSPlaylist reduce(final HLSPlaylist playList) {
        if (playList.videos.isEmpty()) return playList;

        final List<HLSPlaylist.Video> ret = Lists.newArrayList();
        final Iterator<HLSPlaylist.Video> it = playList.videos.iterator();

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

        return new HLSPlaylist(ret, playList.properties);
    }

    public static Optional<VideoSource> parse(final JsonElement element) {
        final String PREVIEW_URL = element.getAsJsonObject().getAsJsonPrimitive("preview").getAsString();

        //                           v                                                        v
        //http://static-cdn.jtvnw.net/v1/AUTH_system/vods_9024/shaboozey_13264185568_208329733/thumb/thumb0-30x240.jpg
        final String MYSTERY_SEGMENT = URI.create(PREVIEW_URL).resolve("..").getPath();

        //Since this is a large hack, we verify it looks as we expect.
        if (MYSTERY_SEGMENT.startsWith("/v1/AUTH_system/")
                && MYSTERY_SEGMENT.endsWith("/")
                && PREVIEW_URL.contains("/thumb/")) {
            final URI playlistUri = TTV_VOD_CDN.resolve(MYSTERY_SEGMENT).resolve("chunked/index-dvr.m3u8");
            logger.debug("Loading HLS playlist from: {}", playlistUri);
            final HLSPlaylist playlist = HLSParser.build(playlistUri)
                    .addKeyHandler("EXT-X-TWITCH-TOTAL-SECS", TTV_TOTAL_SECONDS)
                    .parse();
            return Optional.<VideoSource>of(new HLSVideoSource(
                    playlist,
                    calculateMutedSegments(element.getAsJsonObject())
            ));
        } else {
            throw new UnrecognizedVodFormatException(PREVIEW_URL);
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
        return Objects.toStringHelper(this)
                .add("playlist", playlist)
                .toString();
    }

    @Override
    public int getNumberOfParts() {
        return this.playlist.videos.size();
    }

    @Override
    public int getNumberOfMutedParts() {
        return mutedCount;
    }

    @Override
    public ForkJoinDownloadTask createDownloadTask(File targetFolder, ProgressTracker tracker) {
        return new HLSDownloader(targetFolder, tracker);
    }

    private class HLSDownloader extends ForkJoinDownloadTask {
        private final File targetFolder;
        private final ProgressTracker tracker;

        public HLSDownloader(File targetFolder, ProgressTracker tracker) {
            this.targetFolder = targetFolder;
            this.tracker = tracker;
        }

        @Override
        protected void run() {
            if (playlist.properties.get(HLSHandler.EVENT_ENDED) != Boolean.TRUE) {
                return;
            }

            final List<HLSPartDownloader> downloaders = Lists.newLinkedList();
            int cursor = 0;
            for (final HLSPlaylist.Video v : playlist.videos) {
                final File targetFile = new File(
                        this.targetFolder,
                        String.format(
                                "part%d.%s",
                                ++cursor,
                                com.google.common.io.Files.getFileExtension(v.videoLocation.getPath())
                        )
                );

                if (tracker.getStatus(v.videoLocation.toString()) == ProgressTracker.Status.DOWNLOADED
                        && targetFile.exists()) continue;

                downloaders.add(new HLSPartDownloader(targetFile, v, tracker.track(v.videoLocation.toString())));
            }

            ForkJoinTask.invokeAll(downloaders);

            try (final PrintWriter pw = new PrintWriter(new FileWriter(new File(targetFolder, "ffmpeg-concat.txt")))) {
                cursor = 0;
                for (final HLSPlaylist.Video v : playlist.videos) {
                    final File targetFile = new File(
                            this.targetFolder,
                            String.format(
                                    "part%d.%s",
                                    ++cursor,
                                    com.google.common.io.Files.getFileExtension(v.videoLocation.getPath())
                            )
                    );

                    final Path relativePath = targetFolder.toPath().relativize(targetFile.toPath());

                    if (tracker.getStatus(v.videoLocation.toString()) == ProgressTracker.Status.DOWNLOADED
                            && targetFile.exists()) {
                        pw.printf("file '%s'%n", relativePath);
                    } else {
                        pw.printf("# Missing file '%s'%n", relativePath);
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to save ffmpeg concat file.", e);
            }
        }
    }

    private class HLSPartDownloader extends ForkJoinDownloadTask {
        private final File dest;
        private final HLSPlaylist.Video video;
        private final ProgressTracker.Partial tracker;

        public HLSPartDownloader(File targetFile, HLSPlaylist.Video video, ProgressTracker.Partial tracker) {
            this.dest = targetFile;
            this.video = video;
            this.tracker = tracker;
        }

        @Override
        protected void run() {
            final byte buffer[] = new byte[1024 * 64]; //64kB

            try {
                Files.createFile(dest.toPath());

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
