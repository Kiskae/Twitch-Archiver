package net.serverpeon.twitcharchiver.twitch;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BroadcastInformation {
    public final String title;
    public final int views;
    public final String broadcastId;
    private final int length;
    private final List<VideoSource> sourceList = Lists.newArrayList();
    private final net.serverpeon.twitcharchiver.twitch.VideoSource source;
    private final DateTime recordedAt;

    BroadcastInformation(final String title, final int views, final int length,
                         final String broadcastId, final net.serverpeon.twitcharchiver.twitch.VideoSource source,
                         final DateTime recordedAt
    ) {
        this.title = title;
        this.views = views;
        this.length = length;
        this.broadcastId = broadcastId;
        this.source = source;
        //this.sourceList = sourceList;
        this.recordedAt = recordedAt;
    }

    public long getVideoLength(final TimeUnit tu) {
        return tu.convert(length, TimeUnit.SECONDS);
    }

    public int getViews() {
        return this.views;
    }

    @Deprecated
    public Iterable<VideoSource> getSources() {
        return this.sourceList;
    }

    public net.serverpeon.twitcharchiver.twitch.VideoSource getSource() {
        return this.source;
    }

    public DateTime getRecordedDate() {
        return recordedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BroadcastInformation)) return false;

        BroadcastInformation that = (BroadcastInformation) o;

        return broadcastId.equals(that.broadcastId);
    }

    @Override
    public int hashCode() {
        return broadcastId.hashCode();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("title", title)
                .add("views", views)
                .add("length", length)
                .add("broadcastId", broadcastId)
                .add("recordedAt", recordedAt)
                .add("sourceList", sourceList)
                .toString();
    }

    @Deprecated
    public static class VideoSource {
        public final String videoFileUrl;
        public final int length; //seconds
        public final boolean muted;

        public VideoSource(final String video_file_url, final int length, final boolean muted) {
            this.videoFileUrl = video_file_url;
            this.length = length;
            this.muted = muted;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VideoSource)) return false;

            VideoSource that = (VideoSource) o;

            return videoFileUrl.equals(that.videoFileUrl);

        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("videoFileUrl", videoFileUrl)
                    .add("length", length)
                    .toString();
        }

        @Override
        public int hashCode() {
            return videoFileUrl.hashCode();
        }
    }
}
