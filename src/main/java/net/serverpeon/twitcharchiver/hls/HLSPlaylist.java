package net.serverpeon.twitcharchiver.hls;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class HLSPlaylist {
    public final List<Video> videos;
    public final Map<String, Object> properties;

    public HLSPlaylist(final List<Video> videos, final Map<String, Object> properties) {
        this.videos = ImmutableList.copyOf(videos);
        this.properties = ImmutableMap.copyOf(properties);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("videos", videos)
                .add("properties", properties)
                .toString();
    }

    public static class Video {
        public final URI videoLocation;
        public final long lengthMS; //-1 indicates lack of specified length

        private Video(final URI videoLocation, final long lengthMS) {
            this.videoLocation = videoLocation;
            this.lengthMS = lengthMS;
        }

        public static Video make(final URI videoLocation, final long lengthMS) {
            return new Video(videoLocation, lengthMS);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("videoLocation", videoLocation)
                    .add("lengthMS", lengthMS)
                    .toString();
        }
    }
}
