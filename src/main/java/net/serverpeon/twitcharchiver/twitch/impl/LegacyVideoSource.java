package net.serverpeon.twitcharchiver.twitch.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.serverpeon.twitcharchiver.downloader.ProgressTracker;
import net.serverpeon.twitcharchiver.downloader.VideoSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ForkJoinTask;

public class LegacyVideoSource implements VideoSource {
    private final static Logger logger = LogManager.getLogger(LegacyVideoSource.class);

    private final List<VideoPart> parts;

    private LegacyVideoSource(final List<VideoPart> parts) {
        this.parts = ImmutableList.copyOf(parts);
    }

    public static Optional<VideoSource> parse(final JsonElement element) {
        final JsonArray videos = element.getAsJsonObject()
                .getAsJsonObject("chunks")
                .getAsJsonArray("live");

        final ImmutableList<VideoPart> parts = FluentIterable.from(videos).transform(new Function<JsonElement, JsonObject>() {
            @Override
            public JsonObject apply(JsonElement jsonElement) {
                return jsonElement.getAsJsonObject();
            }
        }).transform(new Function<JsonObject, VideoPart>() {
            @Override
            public VideoPart apply(final JsonObject obj) {
                final String video_file_url = obj.get("url").getAsString();
                final int length = obj.get("length").getAsInt();

                final JsonElement upkeep = obj.get("upkeep");
                final boolean muted = upkeep.isJsonPrimitive() && upkeep.getAsString().equals("fail");

                return new VideoPart(
                        video_file_url,
                        length,
                        muted
                );
            }
        }).toList();

        if (parts.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.<VideoSource>of(new LegacyVideoSource(parts));
        }
    }

    @Override
    public Runnable createDownloadTask(File targetFolder, ProgressTracker progressTracker) {
        return new LegacyDownloader(targetFolder, progressTracker);
    }

    @Override
    public int getNumberOfParts() {
        return parts.size();
    }

    @Override
    public int getNumberOfMutedParts() {
        return FluentIterable.from(parts).filter(new Predicate<VideoPart>() {
            @Override
            public boolean apply(VideoPart videoPart) {
                return videoPart.muted;
            }
        }).size();
    }

    private class LegacyDownloader implements Runnable {
        private final File targetFolder;
        private final ProgressTracker progressTracker;

        LegacyDownloader(File targetFolder, ProgressTracker progressTracker) {
            this.targetFolder = targetFolder;
            this.progressTracker = progressTracker;
        }

        @Override
            final List<LegacyPartDownloader> parts = Lists.newArrayList();

            int cursor = 0;
            for (VideoPart v : LegacyVideoSource.this.parts) {
                final File targetFile = new File(
                        this.targetFolder,
                        String.format(
                                "part%d.%s",
                                ++cursor,
                                com.google.common.io.Files.getFileExtension(v.videoFileUrl)
                        )
                );
        public void run() {

                if (progressTracker.getStatus(v.videoFileUrl) == ProgressTracker.Status.DOWNLOADED
                        && targetFile.exists()) continue;

                parts.add(new LegacyPartDownloader(targetFile, v, progressTracker.track(v.videoFileUrl)));
            }

            ForkJoinTask.invokeAll(parts);
        }
    }

    private class LegacyPartDownloader implements Runnable {
        private final File dest;
        private final VideoPart video;
        private final ProgressTracker.Partial tracker;

        LegacyPartDownloader(final File dest, final VideoPart video, final ProgressTracker.Partial tracker) {
            this.dest = dest;
            this.video = video;
            this.tracker = tracker;
        }

        @Override
        public void run() {
            final byte buffer[] = new byte[1024 * 64]; //64kB

            try {
                Files.createFile(dest.toPath());

                long totalRead = 0;
                logger.debug("Beginning download of {} to {}", video.videoFileUrl, dest);

                final URLConnection conn = new URL(video.videoFileUrl).openConnection();
                final long reportedSize = conn.getContentLengthLong();

                if (!conn.getContentType().contains("video")) {
                    throw new IOException(String.format(
                            "Twitch seems to be returning something that isn't video! (%s)",
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

                if (totalRead < video.length) {
                    //Heuristic, if the video is less than 1bps, its probably corrupted/wrong
                    throw new IOException(String.format(
                            "Invalid file size %d bytes, %d seconds",
                            totalRead,
                            video.length
                    ));
                }

                tracker.finish();

                logger.debug("{} downloaded!", dest);
            } catch (IOException e) {
                logger.warn(new ParameterizedMessage("Error downloading {} to {}", video.videoFileUrl, dest), e);
                dest.delete();
                tracker.invalidate();
            }
        }
    }

    private static class VideoPart {
        public final String videoFileUrl;
        public final int length; //seconds
        public final boolean muted;

        public VideoPart(final String video_file_url, final int length, final boolean muted) {
            this.videoFileUrl = video_file_url;
            this.length = length;
            this.muted = muted;
        }
    }
}