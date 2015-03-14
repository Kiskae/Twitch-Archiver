package net.serverpeon.twitcharchiver.twitch;

import com.google.common.base.MoreObjects;
import net.serverpeon.twitcharchiver.downloader.VideoSource;
import org.joda.time.DateTime;

import java.util.concurrent.TimeUnit;

public class BroadcastInformation {
    public final String title;
    public final int views;
    public final String broadcastId;
    private final int length;
    private final VideoSource source;
    private final DateTime recordedAt;

    BroadcastInformation(final String title, final int views, final int length,
                         final String broadcastId, final VideoSource source,
                         final DateTime recordedAt
    ) {
        this.title = title;
        this.views = views;
        this.length = length;
        this.broadcastId = broadcastId;
        this.source = source;
        this.recordedAt = recordedAt;
    }

    public long getVideoLength(final TimeUnit tu) {
        return tu.convert(length, TimeUnit.SECONDS);
    }

    public int getViews() {
        return this.views;
    }

    public VideoSource getSource() {
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
        return MoreObjects.toStringHelper(this)
                .add("title", title)
                .add("views", views)
                .add("length", length)
                .add("broadcastId", broadcastId)
                .add("recordedAt", recordedAt)
                .add("videoSource", source)
                .toString();
    }
}
